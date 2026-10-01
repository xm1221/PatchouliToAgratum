package cn.xm1221.pta.core.book;

import cn.xm1221.pta.core.book.page.PageRenderContext;
import cn.xm1221.pta.core.book.page.PageTypeRegistry;
import cn.xm1221.pta.core.report.ConversionReport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a book into the Markdown documents Ageratum will read.
 *
 * <p>Each entry becomes one document, and each Patchouli page becomes a section within it. What a
 * section looks like is decided by a {@link PageTypeRegistry}: a page type with a renderer is
 * converted into real Markdown, and anything else is handed to the {@code <pta:page>} component,
 * which renders the original page with Patchouli's own code.</p>
 *
 * <p>That split is the point of the mod. Converted text is searchable, selectable and linkable;
 * hosted pages are exact but inert. Hosting is the safety net, not the goal.</p>
 */
public final class BookConverter {
    /**
     * The document Ageratum opens for {@code /ageratum <namespace>}.
     *
     * <p>Its absence is why the command reports "Guide file not found": the loader resolves
     * {@code ageratum/<language>/index.md} (falling back to {@code en_us}) and nothing else, so a
     * book whose documents are all in subdirectories is unreachable from the command even though
     * every one of them loaded.</p>
     */
    public static final String ROOT_DOCUMENT = "index";

    /**
     * Front matter weight given to a nested chapter's index.
     *
     * <p>Ageratum's sidebar sorts a directory's documents by front matter weight, then by file name,
     * and unweighted documents sit at 0. So a negative weight is what puts sub-chapters above the
     * entries of their parent, and the category's own {@code sortnum} is added back so several
     * sub-chapters keep the order Patchouli gives them.</p>
     */
    private static final int NESTED_CHAPTER_WEIGHT = -1000;

    /**
     * The generated documents.
     *
     * @param documents document path without {@code .md} to the file's full text
     * @param report    what could not be carried across
     */
    public record Output(Map<String, String> documents, ConversionReport report) {
    }

    private BookConverter() {
    }

    /**
     * Converts a loaded book with the standard page renderers.
     *
     * @param layout the book structure
     * @param lang   the flattened language table for the target language
     */
    public static Output convert(BookLayout layout, Map<String, String> lang) {
        return convert(layout, lang, PageTypeRegistry.standard());
    }

    /**
     * Converts a loaded book.
     *
     * @param layout    the book structure
     * @param lang      the flattened language table for the target language
     * @param pageTypes how each page type is rendered
     */
    public static Output convert(BookLayout layout, Map<String, String> lang,
                                 PageTypeRegistry pageTypes) {
        Map<String, String> documents = new LinkedHashMap<>();
        ConversionReport report = ConversionReport.empty();
        BookTextConverter texts = new BookTextConverter(layout, report);

        documents.put(ROOT_DOCUMENT, rootDocument(layout, lang, texts));

        for (BookLayout.Category category : layout.categories().values()) {
            documents.put(category.indexPath(), categoryDocument(layout, category, lang, texts));
        }

        for (BookLayout.Entry entry : layout.entries().values()) {
            documents.put(entry.documentPath(), entryDocument(layout, entry, lang, texts, pageTypes));
            report.document();
        }

        return new Output(Map.copyOf(documents), report);
    }

    // ------------------------------------------------------------------ root document

    /**
     * The book's landing page: its name, its landing text, and links to each root category.
     */
    private static String rootDocument(BookLayout layout, Map<String, String> lang,
                                       BookTextConverter texts) {
        String name = resolve(layout, lang, layout.nameKey(), texts.report());
        if (name.isBlank()) {
            name = layout.bookName();
        }
        String landing = resolve(layout, lang, layout.landingTextKey(), texts.report());

        StringBuilder out = new StringBuilder();
        out.append(frontMatter(name, null, null, false, null));
        out.append("# ").append(name).append('\n');

        if (!landing.isBlank()) {
            out.append('\n')
                    .append(texts.convert(ROOT_DOCUMENT, landing))
                    .append('\n');
        }

        StringBuilder contents = new StringBuilder();
        for (BookLayout.Category category : layout.categories().values()) {
            if (category.parent() != null) {
                continue;
            }
            String title = categoryTitle(layout, category, lang, texts.report());
            contents.append("- [").append(title).append("](")
                    .append(BookLayout.relativize(ROOT_DOCUMENT, category.indexPath()))
                    .append(")\n");
        }
        if (contents.length() > 0) {
            out.append("\n## ").append(name).append(" chapters\n\n").append(contents);
        }
        return out.toString();
    }

    // ------------------------------------------------------------------- categories

    private static String categoryDocument(BookLayout layout, BookLayout.Category category,
                                           Map<String, String> lang, BookTextConverter texts) {
        String name = categoryTitle(layout, category, lang, texts.report());
        String description = resolve(layout, lang, stringField(category.json(), "description"),
                texts.report());

        StringBuilder out = new StringBuilder();
        out.append(frontMatter(name, category.icon(), null, category.secret(), chapterWeight(category)));
        out.append("# ").append(name).append("\n");
        if (!description.isBlank()) {
            out.append('\n')
                    .append(texts.convert(category.indexPath(), description))
                    .append('\n');
        }

        // A chapter's own contents. The sidebar cannot show a nested category's documents, and
        // even at the top level a list here is the only grouped view a reader gets.
        StringBuilder contents = new StringBuilder();
        for (BookLayout.Entry entry : layout.entries().values()) {
            if (!category.id().equals(entry.category())) {
                continue;
            }
            String title = resolve(layout, lang, stringField(entry.json(), "name"), texts.report());
            if (title.isBlank()) {
                title = entry.path();
            }
            contents.append("- [").append(title).append("](")
                    .append(BookLayout.relativize(category.indexPath(), entry.documentPath()))
                    .append(")\n");
        }
        if (contents.length() > 0) {
            out.append('\n').append(contents);
        }
        return out.toString();
    }

    private static String categoryTitle(BookLayout layout, BookLayout.Category category,
                                        Map<String, String> lang, ConversionReport report) {
        String title = resolve(layout, lang, stringField(category.json(), "name"), report);
        return title.isBlank() ? category.path() : title;
    }

    // ---------------------------------------------------------------------- entries

    private static String entryDocument(BookLayout layout, BookLayout.Entry entry,
                                        Map<String, String> lang, BookTextConverter texts,
                                        PageTypeRegistry pageTypes) {
        String name = resolve(layout, lang, stringField(entry.json(), "name"), texts.report());

        StringBuilder body = new StringBuilder();
        List<Object> pages = entry.pages();
        for (int index = 0; index < pages.size(); index++) {
            Map<?, ?> pageJson = normalizePage(pages.get(index));
            if (pageJson == null) {
                continue;
            }
            String section = renderPage(layout, entry, index, pageJson, lang, texts, pageTypes);
            if (section.isBlank()) {
                continue;
            }
            if (body.length() > 0) {
                body.append('\n');
            }
            body.append(section).append('\n');
        }

        StringBuilder out = new StringBuilder();
        out.append(frontMatter(name, entry.icon(), entry.advancement(), entry.secret(), null));
        out.append("# ").append(name).append('\n');
        if (body.length() > 0) {
            out.append('\n').append(body);
        }
        return out.toString();
    }

    /**
     * Normalises one element of a {@code pages} array.
     *
     * <p>Patchouli allows an element to be a bare string, which implicitly means a text page whose
     * text is that string. Every lore entry in Hex Casting is written that way, so skipping
     * non-object pages silently produces documents with a title and no content.</p>
     *
     * @return the page as an object, or {@code null} when it is neither a string nor an object
     */
    private static Map<?, ?> normalizePage(Object page) {
        if (page instanceof Map<?, ?> map) {
            return map;
        }
        if (page instanceof String text) {
            return Map.of("type", "patchouli:text", "text", text);
        }
        return null;
    }

    /**
     * Renders one page through the registry.
     *
     * @return Markdown for the page, possibly empty when it carries nothing
     */
    private static String renderPage(BookLayout layout, BookLayout.Entry entry, int index,
                                     Map<?, ?> pageJson, Map<String, String> lang,
                                     BookTextConverter texts, PageTypeRegistry pageTypes) {
        PageRenderContext context =
                new PageRenderContext(layout, entry, index, pageJson, lang, texts);

        // Ageratum anchors are heading texts and Patchouli anchors look like hexcasting:get_caster,
        // so they cannot be preserved as headings yet. Recorded until a marker component exists.
        String anchor = context.raw("anchor");
        if (anchor != null && !anchor.isBlank()) {
            texts.report().anchor(anchor);
        }

        return pageTypes.render(context);
    }

    // -------------------------------------------------------------------- front matter

    /**
     * The sidebar weight for a category's index.
     *
     * @return a negative weight for a nested chapter, so it precedes its parent's entries, or
     *         {@code null} for a root chapter, whose position is decided elsewhere
     */
    private static Integer chapterWeight(BookLayout.Category category) {
        if (!category.isNested()) {
            return null;
        }
        return NESTED_CHAPTER_WEIGHT + Math.min(category.sortnum(), 999);
    }

    /**
     * Builds the YAML front matter block.
     *
     * <p>{@code items} is the binding that gives Ageratum's "ponder" behaviour for free, so an
     * entry whose icon is an item becomes reachable by holding the ponder key over that item — the
     * closest equivalent Patchouli's entry icon has.</p>
     *
     * @param weight sidebar ordering, or {@code null} to leave the document at the default
     */
    private static String frontMatter(String title, String icon, String advancement,
                                      boolean secret, Integer weight) {
        StringBuilder out = new StringBuilder();
        out.append("---\n");
        out.append("title: \"").append(escapeYaml(title)).append("\"\n");
        String item = itemId(icon);
        if (item != null) {
            out.append("items: \"").append(escapeYaml(item)).append("\"\n");
        }
        if (advancement != null && !advancement.isBlank()) {
            out.append("pta_lock: \"").append(escapeYaml(advancement)).append("\"\n");
        }
        if (secret) {
            out.append("pta_secret: true\n");
        }
        if (weight != null) {
            out.append("weight: ").append(weight).append('\n');
        }
        out.append("---\n\n");
        return out.toString();
    }

    /**
     * Patchouli icons are either an item id or a texture path; only the former can bind.
     */
    private static String itemId(String icon) {
        if (icon == null || icon.isBlank() || icon.endsWith(".png")) {
            return null;
        }
        return icon;
    }

    private static String escapeYaml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // ------------------------------------------------------------------------- helpers

    /**
     * Resolves a language key when the book uses i18n, reporting keys that go nowhere.
     */
    private static String resolve(BookLayout layout, Map<String, String> lang, String key,
                                  ConversionReport report) {
        if (key == null || key.isBlank()) {
            return "";
        }
        if (!layout.usesI18n()) {
            return key;
        }
        String value = lang.get(key);
        if (value == null) {
            report.unrenderedText(key);
            return key;
        }
        return value;
    }

    private static String stringField(Map<?, ?> json, String field) {
        Object value = json.get(field);
        return value instanceof String text ? text : null;
    }

    /** Convenience for callers that only need the document map. */
    public static Map<String, String> documents(BookLayout layout, Map<String, String> lang) {
        return convert(layout, lang).documents();
    }

    /** Deterministic ordering for callers that diff generated output. */
    public static List<String> sortedPaths(Map<String, String> documents) {
        List<String> paths = new ArrayList<>(documents.keySet());
        paths.sort(String::compareTo);
        return paths;
    }
}
