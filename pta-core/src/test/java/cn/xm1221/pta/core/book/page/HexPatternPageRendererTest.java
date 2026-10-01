package cn.xm1221.pta.core.book.page;

import cn.xm1221.pta.core.book.BookLayout;
import cn.xm1221.pta.core.book.BookSource;
import cn.xm1221.pta.core.book.BookTextConverter;
import cn.xm1221.pta.core.lang.Json5;
import cn.xm1221.pta.core.report.ConversionReport;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pattern pages are the one page type where the conversion keeps a dependency's renderer, so these
 * tests are as much about what stays out of the page as about what goes into it: the heading,
 * the signature and the prose must all survive as Ageratum text, and only the hexagon may be
 * delegated.
 *
 * <p>Most of these run against the real Hex Casting book and its real language file when that
 * checkout is present, because the interesting cases — the book-flavoured action names, an empty
 * input slot — only exist in the real data.</p>
 */
class HexPatternPageRendererTest {
    private static final Path HEXMOD_RESOURCES = Path.of("E:/miemod/libs/HexMod/Common/src/main/resources");
    private static final Path HEXMOD_LANG = HEXMOD_RESOURCES.resolve("assets/hexcasting/lang/en_us.flatten.json5");

    private static BookLayout hexmodBook() {
        Assumptions.assumeTrue(Files.isDirectory(HEXMOD_RESOURCES), "HexMod checkout not present");
        return BookLayout.load(
                BookSource.ofDirectory(HEXMOD_RESOURCES, "hexcasting", "thehexbook"),
                "hexcasting", "thehexbook", "en_us");
    }

    private static Map<String, String> hexmodLang() throws IOException {
        Assumptions.assumeTrue(Files.isRegularFile(HEXMOD_LANG), "HexMod language file not present");
        return publishable(Json5.flatten(Files.readString(HEXMOD_LANG, StandardCharsets.UTF_8)));
    }

    /**
     * Rebuilds the key form the published jar actually has.
     *
     * <p>The checkout holds the nested source of the built language file, and its build treats a
     * key ending in {@code :} as a prefix — {@code "hexcasting:"} over {@code get_caster} becomes
     * one key, {@code hexcasting.action.hexcasting:get_caster}. A plain dotted flatten leaves a
     * stray dot in the middle of every action name, which never happens at runtime, where the
     * converter reads the built {@code lang/*.json} out of the mod file.</p>
     */
    private static Map<String, String> publishable(Map<String, String> flattened) {
        Map<String, String> built = new LinkedHashMap<>();
        flattened.forEach((key, value) -> built.put(key.replace(":.", ":"), value));
        return built;
    }

    /** Renders page {@code index} of an entry in the real book. */
    private static String render(BookLayout layout, Map<String, String> lang, String entryId,
                                 int index, ConversionReport report) {
        BookLayout.Entry entry = layout.entries().get(entryId);
        assertNotNull(entry, entryId + " is not in the book");
        Map<?, ?> page = (Map<?, ?>) entry.pages().get(index);
        return PageTypeRegistry.standard().render(new PageRenderContext(
                layout, entry, index, page, lang, new BookTextConverter(layout, report)));
    }

    private static int occurrences(String haystack, String needle) {
        return haystack.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }

    /**
     * The order of the three parts is the layout the user asked for: heading, then the pattern on
     * the left with the prose on the right, and the signature under the pattern rather than in the
     * prose column.
     */
    @Test
    void aPatternPageKeepsItsNameSignatureAndProse() throws IOException {
        BookLayout layout = hexmodBook();
        Map<String, String> lang = hexmodLang();

        String markdown = render(layout, lang, "hexcasting:patterns/basics", 0, ConversionReport.empty());

        // The action's casting name: this checkout's language file predates the book-name
        // overrides its published jar carries, which are covered by
        // theBookNameOfAnActionWinsOverItsCastingName below.
        assertTrue(markdown.startsWith("## Mind's Reflection\n\n"), markdown);
        assertTrue(markdown.contains("<pta:pattern op=\"hexcasting:get_caster\""), markdown);
        // get_caster takes nothing, so the arrow leads, exactly as it reads in the original book.
        assertTrue(markdown.contains("io=\"→ entity | null\""), markdown);
        assertTrue(markdown.contains("Adds me, the caster, to the stack."), markdown);
        assertFalse(markdown.contains("hexcasting.action"), "the language key leaked: " + markdown);

        // One column: the pattern's own band, then the prose. No row, because a row holding a
        // paragraph wraps and would leave the pattern indented beside nothing.
        assertFalse(markdown.contains("<row>"), markdown);
        assertTrue(markdown.indexOf("</pta:pattern>") < markdown.indexOf("Adds me"), markdown);
    }

    /** An action with no book-flavoured name falls back to its casting name. */
    @Test
    void anActionWithoutABookNameUsesItsCastingName() {
        BookLayout layout = hexmodBook();
        ConversionReport report = ConversionReport.empty();

        String markdown = PageTypeRegistry.standard().render(new PageRenderContext(
                layout, layout.entries().get("hexcasting:patterns/basics"), 0,
                Map.of("type", "hexcasting:pattern", "op_id", "hexcasting:example/no_book_name",
                        "text", "body"),
                Map.of("hexcasting.action.hexcasting:example/no_book_name", "Plain Name"),
                new BookTextConverter(layout, report)));

        assertTrue(markdown.startsWith("## Plain Name\n\n"), markdown);
    }

    /**
     * An action that Hex Casting has renamed for its book uses the book name, which is a real
     * difference from the casting UI: "Stadiometer's Prfn." rather than "Stadiometer".
     */
    @Test
    void theBookNameOfAnActionWinsOverItsCastingName() {
        BookLayout layout = hexmodBook();

        String markdown = PageTypeRegistry.standard().render(new PageRenderContext(
                layout, layout.entries().get("hexcasting:patterns/basics"), 0,
                Map.of("type", "hexcasting:pattern", "op_id", "hexcasting:get_entity_height",
                        "text", "body"),
                Map.of("hexcasting.action.hexcasting:get_entity_height", "Stadiometer",
                        "hexcasting.action.book.hexcasting:get_entity_height", "Stadiometer's Prfn."),
                new BookTextConverter(layout, ConversionReport.empty())));

        assertTrue(markdown.startsWith("## Stadiometer's Prfn.\n\n"), markdown);
    }

    /** A page's own {@code header} overrides the action lookup entirely. */
    @Test
    void anExplicitHeaderWins() {
        BookLayout layout = hexmodBook();
        ConversionReport report = ConversionReport.empty();

        String markdown = PageTypeRegistry.standard().render(new PageRenderContext(
                layout, layout.entries().get("hexcasting:patterns/basics"), 0,
                Map.of("type", "hexcasting:pattern", "op_id", "hexcasting:get_caster",
                        "header", "Chapter Two", "text", "body"),
                Map.of("Chapter Two", "Chapter Two", "hexcasting.action.book.hexcasting:get_caster", "Ignored"),
                new BookTextConverter(layout, report)));

        assertTrue(markdown.startsWith("## Chapter Two\n\n"), markdown);
    }

    /** A manual pattern page names its pattern directly and has no signature to show. */
    @Test
    void aManualPatternPageCarriesItsOwnPattern() {
        BookLayout layout = hexmodBook();

        String markdown = PageTypeRegistry.standard().render(new PageRenderContext(
                layout, layout.entries().get("hexcasting:patterns/basics"), 0,
                Map.of("type", "hexcasting:manual_pattern", "patterns", "aqaa",
                        "stroke_order", "false", "text", "body"),
                Map.of(), new BookTextConverter(layout, ConversionReport.empty())));

        assertTrue(markdown.contains("<pta:pattern patterns=\"aqaa\" stroke_order=\"false\"/>"), markdown);
        assertTrue(markdown.contains("body"), markdown);
    }

    /**
     * The shape the real book uses: {@code startdir}/{@code signature}/{@code q}/{@code r} objects,
     * one or several of them, which have to reach Hex Casting as a JSON list.
     */
    @Test
    void aStructuredManualPatternKeepsItsDirectionsAndOffsets() {
        BookLayout layout = hexmodBook();

        String markdown = PageTypeRegistry.standard().render(new PageRenderContext(
                layout, layout.entries().get("hexcasting:patterns/basics"), 0,
                Map.of("type", "hexcasting:manual_pattern_nosig",
                        "patterns", List.of(
                                Map.of("startdir", "NORTH_EAST", "signature", "qaq"),
                                Map.of("startdir", "EAST", "signature", "qaq", "q", 2)),
                        "text", "body"),
                Map.of(), new BookTextConverter(layout, ConversionReport.empty())));

        assertTrue(markdown.contains("patterns=\"NORTH_EAST:qaq;EAST:qaq@2,0\""), markdown);
        // nosig is the page without an input/output line, not without the hint: the original's
        // nosig template carries no stroke-order field, and Hex Casting reads a missing one as true.
        assertTrue(markdown.contains("stroke_order=\"true\""), markdown);
    }

    /** A single object rather than a list, which the book also uses. */
    @Test
    void aSingleStructuredManualPatternIsAccepted() {
        BookLayout layout = hexmodBook();

        String markdown = PageTypeRegistry.standard().render(new PageRenderContext(
                layout, layout.entries().get("hexcasting:patterns/basics"), 0,
                Map.of("type", "hexcasting:manual_pattern",
                        "patterns", Map.of("startdir", "SOUTH_EAST", "signature", "aqaae"),
                        "text", "body"),
                Map.of(), new BookTextConverter(layout, ConversionReport.empty())));

        assertTrue(markdown.contains("patterns=\"SOUTH_EAST:aqaae\""), markdown);
        assertTrue(markdown.contains("stroke_order=\"true\""), markdown);
    }

    /**
     * A manual page draws the patterns it carries even when it also names an action.
     *
     * <p>The action is only the page's name. Hex Casting's manual pages exist for the ops that have
     * no shape to look up — the number pattern, a vector constant, a mask — so taking the action
     * instead of the page's own patterns leaves the page with no picture at all.</p>
     */
    @Test
    void aManualPageDrawsItsOwnPatternsRatherThanItsActions() {
        BookLayout layout = hexmodBook();

        String markdown = PageTypeRegistry.standard().render(new PageRenderContext(
                layout, layout.entries().get("hexcasting:patterns/numbers"), 0,
                Map.of("type", "hexcasting:manual_pattern",
                        "op_id", "hexcasting:number",
                        "patterns", List.of(
                                Map.of("startdir", "SOUTH_EAST", "signature", "aqaa"),
                                Map.of("startdir", "NORTH_EAST", "signature", "dedd", "q", 3)),
                        "text", "body"),
                Map.of(), new BookTextConverter(layout, ConversionReport.empty())));

        assertTrue(markdown.contains("patterns=\"SOUTH_EAST:aqaa;NORTH_EAST:dedd@3,0\""), markdown);
        assertFalse(markdown.contains("<pta:pattern op="), markdown);
    }

    /** A manual page with nothing to draw is not something to guess at. */
    @Test
    void aManualPageWithoutPatternsIsHosted() {
        BookLayout layout = hexmodBook();

        String markdown = PageTypeRegistry.standard().render(new PageRenderContext(
                layout, layout.entries().get("hexcasting:patterns/numbers"), 0,
                Map.of("type", "hexcasting:manual_pattern", "op_id", "hexcasting:number", "text", "body"),
                Map.of(), new BookTextConverter(layout, ConversionReport.empty())));

        assertTrue(markdown.contains("<pta:page"), markdown);
    }

    /** The real page for the number pattern, whose two shapes the book writes out itself. */
    @Test
    void theNumberPatternPageCarriesBothShapes() throws IOException {
        BookLayout layout = hexmodBook();

        String markdown = render(layout, hexmodLang(), "hexcasting:patterns/numbers", 0,
                ConversionReport.empty());

        assertTrue(markdown.contains("patterns=\"SOUTH_EAST:aqaa;NORTH_EAST:dedd@3,0\""), markdown);
        assertFalse(markdown.contains("<pta:pattern op="), markdown);
    }

    /** Nothing to draw means nothing to keep: the page goes back to Patchouli. */
    @Test
    void aPatternPageWithoutAPatternIsHosted() {
        BookLayout layout = hexmodBook();
        ConversionReport report = ConversionReport.empty();

        String markdown = PageTypeRegistry.standard().render(new PageRenderContext(
                layout, layout.entries().get("hexcasting:patterns/basics"), 0,
                Map.of("type", "hexcasting:pattern", "text", "body"),
                Map.of(), new BookTextConverter(layout, report)));

        assertTrue(markdown.contains("<pta:page"), markdown);
        assertTrue(report.hostedPageTypes().containsKey("hexcasting:pattern"), report.toMarkdown());
    }

    /** A page with no prose still shows its pattern, and nothing else. */
    @Test
    void aPatternPageWithoutProseIsJustThePattern() {
        BookLayout layout = hexmodBook();

        String markdown = PageTypeRegistry.standard().render(new PageRenderContext(
                layout, layout.entries().get("hexcasting:patterns/basics"), 0,
                Map.of("type", "hexcasting:pattern", "op_id", "hexcasting:get_caster"),
                Map.of(), new BookTextConverter(layout, ConversionReport.empty())));

        assertFalse(markdown.contains("<row>"), markdown);
        assertTrue(markdown.endsWith("<pta:pattern op=\"hexcasting:get_caster\"/>"), markdown);
    }

    /**
     * The whole book: every pattern page has to convert, and none may fall back to the host.
     */
    @Test
    void everyPatternPageInTheRealBookConverts() throws IOException {
        BookLayout layout = hexmodBook();
        Map<String, String> lang = hexmodLang();
        PageTypeRegistry registry = PageTypeRegistry.standard();

        List<String> hosted = new ArrayList<>();
        int patterns = 0;
        int withSignature = 0;

        for (BookLayout.Entry entry : layout.entries().values()) {
            for (int index = 0; index < entry.pages().size(); index++) {
                if (!(entry.pages().get(index) instanceof Map<?, ?> page)) {
                    continue;
                }
                Object type = page.get("type");
                if (!(type instanceof String text)
                        || !HexPatternPageRenderer.TYPES.contains(text.contains(":") ? text : "patchouli:" + text)) {
                    continue;
                }
                patterns++;

                String markdown = registry.render(new PageRenderContext(layout, entry, index, page,
                        lang, new BookTextConverter(layout, ConversionReport.empty())));
                if (markdown.contains("<pta:page")) {
                    hosted.add(entry.documentPath() + "#" + index);
                    continue;
                }
                assertTrue(markdown.startsWith("## "), "no heading for " + entry.documentPath()
                        + "#" + index + ": " + markdown);
                assertTrue(markdown.contains("<pta:pattern "), markdown);
                if (!"hexcasting:pattern".equals(type)) {
                    // A manual page draws the shapes it carries, never a lookup of the op it names.
                    assertTrue(markdown.contains("patterns=\""), "manual page lost its own pattern: "
                            + entry.documentPath() + "#" + index + ": " + markdown);
                    assertFalse(markdown.contains("<pta:pattern op="), entry.documentPath()
                            + "#" + index + " took its action instead of its patterns: " + markdown);
                }
                if (markdown.contains("io=\"")) {
                    withSignature++;
                }
            }
        }

        assertTrue(patterns > 150, "expected the whole book, found " + patterns + " pattern pages");
        assertTrue(hosted.isEmpty(), "these pattern pages were hosted instead: " + hosted);
        assertTrue(withSignature > 100, "only " + withSignature + " pages kept their signature");
    }

    /** The registry answers all three page types, and only those. */
    @Test
    void thePatternTypesAreRegisteredTogether() {
        PageTypeRegistry registry = PageTypeRegistry.standard();
        for (String type : HexPatternPageRenderer.TYPES) {
            assertNotNull(registry.rendererFor(type), type + " has no renderer");
        }
        assertEquals(3, HexPatternPageRenderer.TYPES.size());
        assertEquals(3, HexPatternPageRenderer.TYPES.stream().distinct().count());
    }
}
