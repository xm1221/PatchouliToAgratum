package cn.xm1221.pta.core.book;

import cn.xm1221.pta.core.lang.Json5;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The structure of one Patchouli book, and the mapping from Patchouli ids onto Ageratum
 * document paths.
 *
 * <h2>The mapping</h2>
 * <p>Patchouli is {@code category → entry → page}; Ageratum is a two-level file tree of
 * Markdown documents. So a category becomes a directory plus an {@code index} document, and an
 * entry becomes one document whose Patchouli pages become sections:</p>
 * <pre>
 * categories/items.json            → items/index.md
 * entries/items/amethyst.json      → items/amethyst.md
 * categories/patterns/great_spells.json → patterns/great_spells/index.md
 * entries/patterns/basics.json     → patterns/basics.md
 * </pre>
 *
 * <p>An entry's document path is taken from its own resource path rather than from its declared
 * category, because that is what Patchouli uses to build the entry id and the two can disagree
 * in a malformed book.</p>
 *
 * <p>Patchouli ids are {@code <book namespace>:<path relative to categories/ or entries/>},
 * without the {@code .json} suffix. See {@code BookContentResourceListenerLoader}'s
 * {@code ID_READER}.</p>
 */
public final class BookLayout {
    /**
     * One Patchouli category.
     *
     * @param id       full id, e.g. {@code hexcasting:items}
     * @param path     path relative to {@code categories/}, e.g. {@code patterns/great_spells}
     * @param parent   parent category id, or {@code null} for a root category
     * @param icon     icon item id or texture, may be {@code null}
     * @param sortnum  ordering hint
     * @param secret   whether the category is hidden until it has unlocked content
     * @param json     the raw object, so later stages can read fields this record does not model
     */
    public record Category(String id, String path, String parent, String icon, int sortnum,
                           boolean secret, Map<String, Object> json) {
        /** The directory holding this category's index document and, usually, its entries. */
        public String directory() {
            return this.path;
        }

        /** The category's landing document. */
        public String indexPath() {
            return this.path + "/index";
        }
    }

    /**
     * One Patchouli entry.
     *
     * @param id          full id, e.g. {@code hexcasting:items/amethyst}
     * @param path        path relative to {@code entries/}, e.g. {@code items/amethyst}
     * @param category    the category this entry declares, may be {@code null} when malformed
     * @param icon        icon item id or texture, may be {@code null}
     * @param advancement advancement that locks the entry, may be {@code null}
     * @param sortnum     ordering hint
     * @param priority    whether the entry sorts to the front
     * @param secret      whether the entry shows as unknown until unlocked
     * @param pages       the raw {@code pages} array
     * @param json        the raw object
     */
    public record Entry(String id, String path, String category, String icon, String advancement,
                        int sortnum, boolean priority, boolean secret, List<Object> pages,
                        Map<String, Object> json) {
        /** The document this entry becomes, without the {@code .md} suffix. */
        public String documentPath() {
            return this.path;
        }
    }

    private final String namespace;
    private final String bookName;
    private final String language;
    private final Map<String, Object> bookJson;
    private final Map<String, Category> categories;
    private final Map<String, Entry> entries;

    private BookLayout(String namespace, String bookName, String language,
                       Map<String, Object> bookJson, Map<String, Category> categories,
                       Map<String, Entry> entries) {
        this.namespace = namespace;
        this.bookName = bookName;
        this.language = language;
        this.bookJson = bookJson;
        this.categories = categories;
        this.entries = entries;
    }

    /**
     * Loads a book.
     *
     * @param source   the book's files
     * @param namespace the mod id owning the book
     * @param bookName  the book's folder name
     * @param language  the language directory to read, e.g. {@code en_us}
     */
    public static BookLayout load(BookSource source, String namespace, String bookName, String language) {
        String bookPath = "data/" + namespace + "/" + BookSource.NAMESPACE_BOOKS + "/" + bookName + "/book.json";
        String bookText = source.read(bookPath);
        if (bookText == null) {
            throw new IllegalArgumentException("no book.json at " + bookPath);
        }
        Map<String, Object> bookJson = asObject(Json5.parse(bookText), bookPath);

        String assetPrefix = "assets/" + namespace + "/" + BookSource.NAMESPACE_BOOKS + "/" + bookName
                + "/" + language + "/";

        Map<String, Category> categories = new LinkedHashMap<>();
        for (String path : source.list(assetPrefix + "categories", ".json")) {
            String relative = relativeId(path, assetPrefix + "categories/");
            if (relative == null) {
                continue;
            }
            Map<String, Object> json = asObject(Json5.parse(source.read(path)), path);
            String id = namespace + ":" + relative;
            categories.put(id, new Category(
                    id,
                    relative,
                    stringOrNull(json.get("parent")),
                    stringOrNull(json.get("icon")),
                    intOr(json.get("sortnum"), 0),
                    boolOr(json.get("secret"), false),
                    json));
        }

        Map<String, Entry> entries = new LinkedHashMap<>();
        for (String path : source.list(assetPrefix + "entries", ".json")) {
            String relative = relativeId(path, assetPrefix + "entries/");
            if (relative == null) {
                continue;
            }
            Map<String, Object> json = asObject(Json5.parse(source.read(path)), path);
            String id = namespace + ":" + relative;
            entries.put(id, new Entry(
                    id,
                    relative,
                    stringOrNull(json.get("category")),
                    stringOrNull(json.get("icon")),
                    stringOrNull(json.get("advancement")),
                    intOr(json.get("sortnum"), 0),
                    boolOr(json.get("priority"), false),
                    boolOr(json.get("secret"), false),
                    listOrEmpty(json.get("pages")),
                    json));
        }

        return new BookLayout(namespace, bookName, language, bookJson, categories, entries);
    }

    public String namespace() {
        return this.namespace;
    }

    public String bookName() {
        return this.bookName;
    }

    /** The book id as Patchouli knows it, e.g. {@code hexcasting:thehexbook}. */
    public String bookId() {
        return this.namespace + ":" + this.bookName;
    }

    public String language() {
        return this.language;
    }

    public boolean usesI18n() {
        return boolOr(this.bookJson.get("i18n"), false);
    }

    /** The book's own macro table. */
    public Map<String, String> macros() {
        Map<String, String> result = new LinkedHashMap<>();
        Object macros = this.bookJson.get("macros");
        if (macros instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }
        return result;
    }

    public Map<String, Category> categories() {
        return this.categories;
    }

    public Map<String, Entry> entries() {
        return this.entries;
    }

    /**
     * Resolves a Patchouli link target to a document path.
     *
     * @param target a Patchouli entry or category id, with or without its namespace
     * @return the document path without {@code .md}, or {@code null} when nothing matches
     */
    public String resolveTarget(String target) {
        if (target == null || target.isBlank()) {
            return null;
        }
        String qualified = target.contains(":") ? target : this.namespace + ":" + target;
        Entry entry = this.entries.get(qualified);
        if (entry != null) {
            // An entry wins over a category with the same id, which is what Patchouli does too.
            return entry.documentPath();
        }
        Category category = this.categories.get(qualified);
        return category == null ? null : category.indexPath();
    }

    /**
     * Rewrites a target as a path relative to the linking document's directory.
     *
     * <p>Ageratum resolves a link without a namespace against the current document's directory
     * first and the namespace root second; emitting a genuinely relative path hits the first
     * attempt unambiguously instead of relying on the fallback not colliding.</p>
     *
     * @param fromDocumentPath the linking document, e.g. {@code items/amethyst}
     * @param targetPath       the target document, e.g. {@code patterns/basics}
     */
    public static String relativize(String fromDocumentPath, String targetPath) {
        String fromDirectory = directoryOf(fromDocumentPath);
        // "" .split("/") yields [""], not [], so an empty directory needs handling explicitly.
        String[] from = fromDirectory.isEmpty() ? new String[0] : fromDirectory.split("/");
        String[] to = targetPath.split("/");

        int common = 0;
        while (common < from.length && common < to.length - 1 && from[common].equals(to[common])) {
            common++;
        }

        StringBuilder result = new StringBuilder();
        for (int i = common; i < from.length; i++) {
            result.append("../");
        }
        for (int i = common; i < to.length; i++) {
            result.append(to[i]);
            if (i < to.length - 1) {
                result.append('/');
            }
        }
        return result.toString();
    }

    /**
     * The directory holding a document, or an empty string for a top-level document.
     */
    public static String directoryOf(String documentPath) {
        int slash = documentPath.lastIndexOf('/');
        return slash < 0 ? "" : documentPath.substring(0, slash);
    }

    // ------------------------------------------------------------------- parsing helpers

    /**
     * Turns {@code .../entries/items/amethyst.json} into {@code items/amethyst}.
     */
    private static String relativeId(String resourcePath, String folderPrefix) {
        if (!resourcePath.startsWith(folderPrefix) || !resourcePath.endsWith(".json")) {
            return null;
        }
        return resourcePath.substring(folderPrefix.length(), resourcePath.length() - ".json".length());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asObject(Object parsed, String where) {
        if (parsed instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalArgumentException("expected a JSON object at " + where);
    }

    private static String stringOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isEmpty() ? null : text;
    }

    private static int intOr(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return fallback;
    }

    private static boolean boolOr(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private static List<Object> listOrEmpty(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return List.of();
    }

    @Override
    public String toString() {
        return "BookLayout[" + bookId() + ", " + Objects.toString(this.language, "?")
                + ", " + this.categories.size() + " categories, " + this.entries.size() + " entries]";
    }
}
