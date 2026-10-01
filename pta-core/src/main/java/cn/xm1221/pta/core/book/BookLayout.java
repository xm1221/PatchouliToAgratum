package cn.xm1221.pta.core.book;

import cn.xm1221.pta.core.lang.Json5;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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

        /**
         * The category's landing document.
         *
         * <p>Capped at one directory, not two. Ageratum's sidebar renders a child directory's index
         * in a separate pass that runs <i>after</i> the parent's own documents, so a nested chapter
         * would always sit below every entry of its parent. Flattening it into the parent directory
         * makes it an ordinary document there, which the sidebar sorts by front matter weight — and
         * that is what lets a sub-chapter be listed before the parent's entries, which is how a
         * reader expects a table of contents to read.</p>
         */
        public String indexPath() {
            return limitDepth(this.path + "/index", 1);
        }

        /** Whether this category sits inside another one. */
        public boolean isNested() {
            return this.path.indexOf('/') >= 0;
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
        /**
         * The document this entry becomes, without the {@code .md} suffix.
         *
         * <p>Flattened to at most one directory. Ageratum renders a two-level sidebar: a
         * top-level directory contributes its own documents as second-level entries, but a
         * <i>child</i> directory contributes only its {@code index} document and none of its
         * documents. Leaving a nested category's entries where Patchouli puts them therefore makes
         * them unreachable, so the nesting is folded into the file name instead — the sidebar
         * shows a document's title, not its file name.</p>
         */
        public String documentPath() {
            return limitDepth(this.path, 1);
        }
    }

    private final String namespace;
    private final String bookName;
    private final String language;
    private final BookSource source;
    private final Map<String, Object> bookJson;
    private final Map<String, Category> categories;
    private final Map<String, Entry> entries;

    private BookLayout(String namespace, String bookName, String language, BookSource source,
                       Map<String, Object> bookJson, Map<String, Category> categories,
                       Map<String, Entry> entries) {
        this.namespace = namespace;
        this.bookName = bookName;
        this.language = language;
        this.source = source;
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
            // Naming the source matters: "not found" in a jar-backed source and "not found" on
            // disk have completely different causes.
            throw new IllegalArgumentException("no book.json at " + bookPath + " in " + source);
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

        return new BookLayout(namespace, bookName, language, source, bookJson, categories, entries);
    }

    public String namespace() {
        return this.namespace;
    }

    /**
     * The book's files.
     *
     * <p>Kept so that a renderer can follow a page to data the page only names — a recipe page
     * names a recipe id, and the recipe itself is a file beside the book rather than part of
     * it.</p>
     */
    public BookSource source() {
        return this.source;
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

    /**
     * The book's display name. A language key when the book uses i18n.
     */
    public String nameKey() {
        return stringOrNull(this.bookJson.get("name"));
    }

    /**
     * The book's landing text. A language key when the book uses i18n.
     */
    public String landingTextKey() {
        return stringOrNull(this.bookJson.get("landing_text"));
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

    // ------------------------------------------------------------------- locking

    /**
     * Whether Patchouli would show this chapter as locked.
     *
     * <p>A category has no {@code advancement} of its own — the field does not exist in Patchouli's
     * category format. A chapter instead counts as locked while it holds something and every entry
     * and every sub-chapter in it is locked, which is what
     * {@code BookCategory.updateLockStatus} computes. Mirroring the rule rather than inventing a
     * per-chapter advancement keeps the guide's chapters as reachable as the original book's.</p>
     *
     * <p>Patchouli's own {@code advancementsEnabled} switch is deliberately not consulted: it comes
     * from Patchouli's configuration rather than from the book, and a guide should be locked
     * according to what the book declares.</p>
     */
    public boolean isCategoryLocked(String categoryId) {
        return isCategoryLocked(categoryId, new LinkedHashMap<>());
    }

    private boolean isCategoryLocked(String categoryId, Map<String, Boolean> memo) {
        Boolean known = memo.get(categoryId);
        if (known != null) {
            return known;
        }
        Category category = this.categories.get(categoryId);
        if (category == null) {
            return false;
        }

        List<Category> children = childCategories(categoryId);
        List<Entry> entries = categoryEntries(categoryId);
        // Patchouli starts from "locked" only if the category holds anything at all, so an empty
        // chapter never locks — and, being empty, never locks its parent either.
        boolean locked = !children.isEmpty() || !entries.isEmpty();
        for (int index = 0; locked && index < children.size(); index++) {
            locked = isCategoryLocked(children.get(index).id(), memo);
        }
        for (int index = 0; locked && index < entries.size(); index++) {
            locked = isEntryLocked(entries.get(index));
        }

        memo.put(categoryId, locked);
        return locked;
    }

    /** Whether Patchouli would call this entry locked: it names an advancement. */
    public static boolean isEntryLocked(Entry entry) {
        return entry.advancement() != null && !entry.advancement().isBlank();
    }

    /**
     * The advancements declared by every entry inside a chapter, including its sub-chapters.
     *
     * <p>A locked chapter unlocks as soon as any of them is earned, so this is what a chapter-level
     * lock has to ask about. Order follows the layout, and duplicates are folded away: the same
     * advancement often locks several entries of the same chapter.</p>
     */
    public List<String> categoryAdvancements(String categoryId) {
        Set<String> collected = new LinkedHashSet<>();
        collectAdvancements(categoryId, collected, new HashSet<>());
        return List.copyOf(collected);
    }

    private void collectAdvancements(String categoryId, Set<String> collected, Set<String> seen) {
        if (!seen.add(categoryId)) {
            return;
        }
        for (Entry entry : categoryEntries(categoryId)) {
            if (isEntryLocked(entry)) {
                collected.add(entry.advancement().trim());
            }
        }
        for (Category child : childCategories(categoryId)) {
            collectAdvancements(child.id(), collected, seen);
        }
    }

    /** The entries declaring this chapter directly. */
    public List<Entry> categoryEntries(String categoryId) {
        List<Entry> found = new ArrayList<>();
        for (Entry entry : this.entries.values()) {
            if (categoryId.equals(qualify(entry.category()))) {
                found.add(entry);
            }
        }
        return found;
    }

    /** The chapters declaring this chapter as their parent. */
    public List<Category> childCategories(String categoryId) {
        List<Category> found = new ArrayList<>();
        for (Category category : this.categories.values()) {
            if (categoryId.equals(qualify(category.parent()))) {
                found.add(category);
            }
        }
        return found;
    }

    /** A category id as written in a {@code parent} field, with this book's namespace added. */
    private String qualify(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return id.contains(":") ? id : this.namespace + ":" + id;
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

    /**
     * Folds a path's deeper segments into its last kept one.
     *
     * <p>{@code limitDepth("patterns/great_spells/foo", 1)} is {@code patterns/great_spells__foo}:
     * one directory, and a file name that still sorts next to its siblings. Ageratum's sidebar
     * only reaches one level of child directory, so anything deeper would be invisible.</p>
     *
     * @param path           a document or directory path
     * @param maxDirectories how many leading segments may remain as directories
     */
    public static String limitDepth(String path, int maxDirectories) {
        String[] segments = path.split("/");
        if (segments.length <= maxDirectories + 1) {
            return path;
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < maxDirectories; i++) {
            out.append(segments[i]).append('/');
        }
        for (int i = maxDirectories; i < segments.length; i++) {
            if (i > maxDirectories) {
                out.append("__");
            }
            out.append(segments[i]);
        }
        return out.toString();
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
