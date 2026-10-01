package cn.xm1221.pta.core.book;

import cn.xm1221.pta.core.report.ConversionReport;
import cn.xm1221.pta.core.text.AgeratumTextWriter;
import cn.xm1221.pta.core.text.MacroExpander;
import cn.xm1221.pta.core.text.PatchouliTextScanner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a book into the Markdown documents Ageratum will read.
 *
 * <p>Each entry becomes one document. Pages become sections within it, except that a text page is
 * converted into real Markdown while every other page type is handed to the {@code <pta:page>}
 * component, which renders the original Patchouli page. That split is the point of the mod: text
 * becomes searchable, selectable and linkable, and everything that draws something is left to
 * Patchouli rather than re-implemented.</p>
 */
public final class BookConverter {
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
     * Converts a loaded book.
     *
     * @param layout the book structure
     * @param lang   the flattened language table for the target language
     */
    public static Output convert(BookLayout layout, Map<String, String> lang) {
        Map<String, String> documents = new LinkedHashMap<>();
        ConversionReport report = ConversionReport.empty();
        MacroExpander expander = MacroExpander.of(layout.macros());

        for (BookLayout.Category category : layout.categories().values()) {
            documents.put(category.indexPath(), categoryDocument(layout, category, lang, report));
        }

        for (BookLayout.Entry entry : layout.entries().values()) {
            documents.put(entry.documentPath(), entryDocument(layout, entry, lang, report, expander));
            report.document();
        }

        return new Output(Map.copyOf(documents), report);
    }

    // ------------------------------------------------------------------- categories

    private static String categoryDocument(BookLayout layout, BookLayout.Category category,
                                           Map<String, String> lang, ConversionReport report) {
        String name = resolve(layout, lang, stringField(category.json(), "name"), report);
        String description = resolve(layout, lang, stringField(category.json(), "description"), report);

        StringBuilder out = new StringBuilder();
        out.append(frontMatter(name, category.icon(), null, category.secret()));
        out.append("# ").append(name).append("\n");
        if (!description.isBlank()) {
            out.append('\n')
                    .append(convertText(layout, MacroExpander.of(layout.macros()),
                            category.indexPath(), description, report))
                    .append('\n');
        }
        return out.toString();
    }

    // ---------------------------------------------------------------------- entries

    private static String entryDocument(BookLayout layout, BookLayout.Entry entry,
                                        Map<String, String> lang, ConversionReport report,
                                        MacroExpander expander) {
        String name = resolve(layout, lang, stringField(entry.json(), "name"), report);

        StringBuilder body = new StringBuilder();
        List<Object> pages = entry.pages();
        for (int index = 0; index < pages.size(); index++) {
            Object page = pages.get(index);
            if (!(page instanceof Map<?, ?> pageJson)) {
                continue;
            }
            String section = renderPage(layout, entry, index, pageJson, lang, report, expander);
            if (section.isBlank()) {
                continue;
            }
            if (body.length() > 0) {
                body.append('\n');
            }
            body.append(section).append('\n');
        }

        StringBuilder out = new StringBuilder();
        out.append(frontMatter(name, entry.icon(), entry.advancement(), entry.secret()));
        out.append("# ").append(name).append('\n');
        if (body.length() > 0) {
            out.append('\n').append(body);
        }
        return out.toString();
    }

    /**
     * Renders one page.
     *
     * @return Markdown for the page, possibly empty when the page carries nothing
     */
    private static String renderPage(BookLayout layout, BookLayout.Entry entry, int index,
                                     Map<?, ?> pageJson, Map<String, String> lang,
                                     ConversionReport report, MacroExpander expander) {
        String type = stringField(pageJson, "type");
        if (type == null || type.isBlank()) {
            type = "patchouli:text";
        }
        if (!type.contains(":")) {
            type = "patchouli:" + type;
        }

        String anchor = stringField(pageJson, "anchor");
        if (anchor != null) {
            // Ageratum anchors are heading texts, and Patchouli anchors look like
            // hexcasting:get_caster, so they cannot be preserved as headings. Recorded until a
            // dedicated marker component exists.
            report.anchor(anchor);
        }

        String title = resolve(layout, lang, stringField(pageJson, "title"), report);

        if (type.equals("patchouli:text") || type.equals("patchouli:link")) {
            String text = resolve(layout, lang, stringField(pageJson, "text"), report);
            StringBuilder section = new StringBuilder();
            if (!title.isBlank()) {
                section.append("## ").append(title).append("\n\n");
            }
            section.append(convertText(layout, expander, entry.documentPath(), text, report));

            String url = stringField(pageJson, "url");
            if (type.equals("patchouli:link") && url != null) {
                String linkText = resolve(layout, lang, stringField(pageJson, "link_text"), report);
                section.append("\n\n[").append(linkText.isBlank() ? url : linkText)
                        .append("](").append(url).append(')');
            }
            return section.toString();
        }

        // Everything else draws something, so hand the page to Patchouli through the component.
        report.unsupportedPageType(type);
        StringBuilder section = new StringBuilder();
        if (!title.isBlank()) {
            section.append("## ").append(title).append("\n\n");
        }
        section.append("<pta:page book=\"").append(layout.bookId())
                .append("\" entry=\"").append(entry.path())
                .append("\" page=\"").append(index).append("\"/>");
        return section.toString();
    }

    /**
     * Converts one run of book text, rewriting its links relative to the document it will live in.
     *
     * @param fromDocumentPath the document this text becomes part of, used to relativise links
     */
    private static String convertText(BookLayout layout, MacroExpander expander,
                                      String fromDocumentPath, String text,
                                      ConversionReport report) {
        if (text == null || text.isBlank()) {
            return "";
        }
        AgeratumTextWriter.Result result = AgeratumTextWriter.write(
                PatchouliTextScanner.scan(expander.expand(text).orThrow()),
                (target, anchor, external) -> {
                    if (external) {
                        return target;
                    }
                    String resolved = layout.resolveTarget(target);
                    if (resolved == null) {
                        return target;
                    }
                    if (anchor != null) {
                        report.anchor(target);
                    }
                    return BookLayout.relativize(fromDocumentPath, resolved);
                });
        for (int i = 0; i < result.droppedUnderlines(); i++) {
            report.underline();
        }
        for (int i = 0; i < result.droppedPlayerNames(); i++) {
            report.playerName();
        }
        result.unknownCommands().forEach(report::unknownCommand);
        return result.markdown();
    }

    // -------------------------------------------------------------------- front matter

    /**
     * Builds the YAML front matter block.
     *
     * <p>{@code items} is the binding that gives Ageratum's "ponder" behaviour for free, so an
     * entry whose icon is an item becomes reachable by holding the ponder key over that item —
     * the closest equivalent Patchouli's entry icon has.</p>
     */
    private static String frontMatter(String title, String icon, String advancement,
                                      boolean secret) {
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
