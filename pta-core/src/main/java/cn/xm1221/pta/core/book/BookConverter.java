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
        out.append(frontMatter(name, null, false, null));
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

        StringBuilder document = new StringBuilder();
        document.append("# ").append(name).append("\n");
        if (!description.isBlank()) {
            document.append('\n')
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
            document.append('\n').append(contents);
        }

        StringBuilder out = new StringBuilder();
        out.append(frontMatter(name, null, category.secret(), chapterWeight(category)));
        out.append(chapterLock(layout, category, document.toString()));
        return out.toString();
    }

    /**
     * Locks a chapter's index while the chapter holds nothing unlocked.
     *
     * <p>A chapter has no advancement of its own, so what it is waiting for is the advancements of
     * the entries inside it — and, as in Patchouli, any one of them being earned opens the chapter.
     * The list is the chapter's whole contents, so the lock also hides which entries it holds, which
     * is exactly what Patchouli does not show for a locked chapter.</p>
     */
    private static String chapterLock(BookLayout layout, BookLayout.Category category, String document) {
        if (!layout.isCategoryLocked(category.id())) {
            return document;
        }
        List<String> advancements = layout.categoryAdvancements(category.id());
        if (advancements.isEmpty()) {
            // Unreachable: a locked chapter has locked entries, and a locked entry names an
            // advancement. Guarded anyway, because an empty requirement would lock forever.
            return document;
        }
        return locked(String.join(",", advancements), true, category.secret(), document);
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
        out.append(frontMatter(name, entry.advancement(), entry.secret(), null));
        out.append(entryLock(entry, document(name, body)));
        return out.toString();
    }

    /** The entry's body: its title, then its pages. */
    private static String document(String name, StringBuilder body) {
        StringBuilder document = new StringBuilder();
        document.append("# ").append(name).append('\n');
        if (body.length() > 0) {
            document.append('\n').append(body);
        }
        return document.toString();
    }

    /** Locks an entry's body behind the advancement the book declared for it. */
    private static String entryLock(BookLayout.Entry entry, String document) {
        if (!BookLayout.isEntryLocked(entry)) {
            return document;
        }
        return locked(entry.advancement().trim(), false, entry.secret(), document);
    }

    /**
     * Wraps a document body in a lock the client checks while drawing it.
     *
     * <p>Whether an advancement is earned is per player and changes while the game runs, so it
     * cannot be decided here: the document can only carry the requirement. The client decides, and
     * {@code <pta:locked>} — an Ageratum component this mod contributes — then draws either the body
     * or a notice saying it is not unlocked yet.</p>
     *
     * <p>The front matter keeps {@code pta_lock} as well, so an exported or inspected document says
     * what it is waiting for; the component is what actually enforces it.</p>
     *
     * @param advancements the advancement ids that unlock the body, comma separated
     * @param unlockAny    whether any one of them is enough, as a locked chapter needs
     * @param secret       whether the requirement itself is a secret, as Patchouli treats it
     */
    private static String locked(String advancements, boolean unlockAny, boolean secret,
                                 String document) {
        StringBuilder out = new StringBuilder();
        out.append("<pta:locked advancements=\"").append(attribute(advancements)).append('"');
        if (unlockAny) {
            out.append(" unlock=\"any\"");
        }
        if (secret) {
            out.append(" secret=\"true\"");
        }
        out.append(">\n\n").append(document).append("\n</pta:locked>\n");
        return out.toString();
    }

    /** Keeps a requirement from ending the tag it is written into. */
    private static String attribute(String value) {
        return value.replace("\"", "").replace("\n", " ").trim();
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
     * <p>Only what the book itself said goes in: its name, its lock, its secrecy, its order. The
     * entry icon is deliberately not written as an {@code items} binding — that would make the
     * guide respond to the player holding an item, which is behaviour the original book does not
     * have, and a converted book should not gain features its source never had.</p>
     *
     * @param weight sidebar ordering, or {@code null} to leave the document at the default
     */
    private static String frontMatter(String title, String advancement, boolean secret,
                                      Integer weight) {
        StringBuilder out = new StringBuilder();
        out.append("---\n");
        out.append("title: \"").append(escapeYaml(title)).append("\"\n");
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
