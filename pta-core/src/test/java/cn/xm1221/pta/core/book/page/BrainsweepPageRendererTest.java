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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Brainsweep pages, which are Hex Casting's alone.
 *
 * <p>A page names a recipe rather than carrying one, so these tests run against two things at
 * once: the page and the recipe file the page points at. The recipe is where every part of the
 * picture comes from — the mob, the block, the media, the result — which is why a page whose
 * recipe cannot be read has to be hosted rather than guessed at.</p>
 */
class BrainsweepPageRendererTest {
    private static final Path HEXMOD_RESOURCES = Path.of("E:/miemod/libs/HexMod/Common/src/main/resources");
    private static final Path HEXMOD_GENERATED = Path.of("E:/miemod/libs/HexMod/Common/src/generated/resources");
    private static final Path HEXMOD_LANG = HEXMOD_RESOURCES.resolve("assets/hexcasting/lang/en_us.flatten.json5");

    /** The book's own page for a villager swept into budding amethyst. */
    private static final String BUDDING_AMETHYST = """
            { type: "hexcasting:brainsweep",
              blockIn: { type: "hexcasting:block", block: "minecraft:amethyst_block" },
              cost: 1000000,
              entityIn: { type: "hexcasting:villager", minLevel: 3 },
              result: { Name: "minecraft:budding_amethyst" } }
            """;

    /**
     * A book with one recipe beside it.
     *
     * <p>{@code i18n} is off, so the page's prose is the text it carries — the real book keeps its
     * prose in a language file, which says nothing about how a page is laid out.</p>
     */
    private static BookLayout demoBook(String recipe) {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("data/demo/patchouli_books/demo/book.json", "{ name: \"Demo\", i18n: false }");
        files.put("assets/demo/patchouli_books/demo/en_us/categories/demo.json",
                "{ name: \"Demo\", description: \"d\", icon: \"minecraft:book\" }");
        files.put("assets/demo/patchouli_books/demo/en_us/entries/item.json",
                "{ name: \"Item\", icon: \"minecraft:diamond\", category: \"demo:demo\", pages: [] }");
        files.put("data/demo/recipe/brainsweep/demo.json", recipe);
        return BookLayout.load(BookSource.of(files), "demo", "demo", "en_us");
    }

    private static String render(String recipe, Map<String, Object> page, ConversionReport report) {
        BookLayout layout = demoBook(recipe);
        return PageTypeRegistry.standard().render(new PageRenderContext(
                layout, layout.entries().get("demo:item"), 0, page, Map.of(),
                new BookTextConverter(layout, report)));
    }

    private static String render(Map<String, Object> page) {
        return render(BUDDING_AMETHYST, page, ConversionReport.empty());
    }

    private static Map<String, Object> page(String recipe) {
        return Map.of("type", "hexcasting:brainsweep", "recipe", recipe, "text", "The prose.");
    }

    /**
     * The recipe is one centred row, in the order it happens: the mob, the block it is swept into,
     * what the sweep costs, and what comes out.
     */
    @Test
    void aBrainsweepBecomesACentredRecipeStrip() {
        String markdown = render(page("demo:brainsweep/demo"));

        assertTrue(markdown.startsWith("<row halign=\"center\" valign=\"center\">"), markdown);
        assertTrue(markdown.contains("<entity id=\"minecraft:villager\""), markdown);
        assertTrue(markdown.contains("<block id=\"minecraft:amethyst_block\" showText=\"false\"/>"), markdown);
        assertTrue(markdown.contains("<item id=\"hexcasting:charged_amethyst\" count=\"10\" showText=\"false\"/>"),
                markdown);
        assertTrue(markdown.contains("<block id=\"minecraft:budding_amethyst\" showText=\"false\"/>"), markdown);
        assertTrue(markdown.endsWith("The prose."), markdown);
        assertFalse(markdown.contains("<pta:page"), markdown);
    }

    /** The row's four parts are its children, separated the way a row separates them. */
    @Test
    void thePartsAreSeparatedIntoOneRow() {
        String markdown = render(page("demo:brainsweep/demo"));

        assertEquals(3, occurrences(markdown, "\n---\n"), markdown);
    }

    /**
     * The mob is drawn as Patchouli draws it: the profession the recipe asks for, plains when it
     * asks for no particular kind, and level 1 — a picture, not a statement of the requirement,
     * exactly as the template Hex Casting ships shows it.
     */
    @Test
    void theVillagerIsDrawnAsTheRecipeDescribesIt() {
        String markdown = render(BUDDING_AMETHYST, page("demo:brainsweep/demo"), ConversionReport.empty());

        assertTrue(markdown.contains("nbt='{VillagerData:{profession:\"minecraft:toolsmith\","
                + "type:\"minecraft:plains\",level:1}}'"), markdown);
    }

    @Test
    void aNamedProfessionIsKept() {
        String recipe = """
                { type: "hexcasting:brainsweep",
                  blockIn: { type: "hexcasting:block", block: "minecraft:amethyst_block" },
                  cost: 1000000,
                  entityIn: { type: "hexcasting:villager", minLevel: 5, profession: "minecraft:librarian" },
                  result: { Name: "hexcasting:akashic_record" } }
                """;
        String markdown = render(recipe, page("demo:brainsweep/demo"), ConversionReport.empty());

        assertTrue(markdown.contains("profession:\"minecraft:librarian\""), markdown);
    }

    /** A mob named by type carries no data, so it is drawn as a plain entity. */
    @Test
    void aMobNamedByTypeIsDrawnWithoutData() {
        String recipe = """
                { type: "hexcasting:brainsweep",
                  blockIn: { type: "hexcasting:block", block: "minecraft:amethyst_block" },
                  cost: 100000,
                  entityIn: { type: "hexcasting:entity_type", entityType: "minecraft:allay" },
                  result: { Name: "hexcasting:quenched_allay" } }
                """;
        String markdown = render(recipe, page("demo:brainsweep/demo"), ConversionReport.empty());

        assertTrue(markdown.contains("<entity id=\"minecraft:allay\" showText=\"false\"/>"), markdown);
        assertFalse(markdown.contains("nbt="), markdown);
        assertTrue(markdown.contains("<item id=\"hexcasting:charged_amethyst\" count=\"1\" showText=\"false\"/>"),
                markdown);
    }

    /** A cost of whole units is stated in the largest one that divides it. */
    @Test
    void mediaIsStatedInTheLargestWholeUnit() {
        assertTrue(render(cost(10000), page("demo:brainsweep/demo"), ConversionReport.empty())
                .contains("<item id=\"hexcasting:amethyst_dust\" count=\"1\""));
        assertTrue(render(cost(50000), page("demo:brainsweep/demo"), ConversionReport.empty())
                .contains("<item id=\"minecraft:amethyst_shard\" count=\"1\""));
        assertTrue(render(cost(150000), page("demo:brainsweep/demo"), ConversionReport.empty())
                .contains("<item id=\"minecraft:amethyst_shard\" count=\"3\""));
    }

    /** Media that is not a whole number of amethyst cannot be stated without rounding it up. */
    @Test
    void aCostThatIsNotWholeUnitsIsHostedInstead() {
        ConversionReport report = ConversionReport.empty();
        String markdown = render(cost(12345), page("demo:brainsweep/demo"), report);

        assertTrue(markdown.contains("<pta:page"), markdown);
        assertEquals(Map.of("hexcasting:brainsweep", 1), report.hostedPageTypes());
    }

    @Test
    void aMobGivenByTagIsHostedInstead() {
        String recipe = """
                { type: "hexcasting:brainsweep",
                  blockIn: { type: "hexcasting:block", block: "minecraft:amethyst_block" },
                  cost: 1000000,
                  entityIn: { type: "hexcasting:entity_tag", tag: "minecraft:raiders" },
                  result: { Name: "minecraft:budding_amethyst" } }
                """;
        String markdown = render(recipe, page("demo:brainsweep/demo"), ConversionReport.empty());

        assertTrue(markdown.contains("<pta:page"), markdown);
    }

    @Test
    void aBlockGivenByTagIsHostedInstead() {
        String recipe = """
                { type: "hexcasting:brainsweep",
                  blockIn: { type: "hexcasting:tag", tag: "minecraft:logs" },
                  cost: 1000000,
                  entityIn: { type: "hexcasting:villager", minLevel: 3 },
                  result: { Name: "minecraft:budding_amethyst" } }
                """;
        String markdown = render(recipe, page("demo:brainsweep/demo"), ConversionReport.empty());

        assertTrue(markdown.contains("<pta:page"), markdown);
    }

    /** A page whose recipe is not there is a page nothing is known about. */
    @Test
    void aMissingRecipeIsHostedInstead() {
        String markdown = render(BUDDING_AMETHYST, page("demo:brainsweep/nope"), ConversionReport.empty());

        assertTrue(markdown.contains("<pta:page"), markdown);
    }

    /**
     * The real book, where the point is that nothing is left to Patchouli: every page is a page
     * with a recipe beside it in the mod's own data.
     */
    @Test
    void theRealBrainsweepingEntryIsConvertedWhole() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(HEXMOD_RESOURCES), "HexMod checkout not present");
        Assumptions.assumeTrue(Files.isRegularFile(HEXMOD_LANG), "HexMod language file not present");

        BookLayout layout = BookLayout.load(
                BookSource.ofDirectories(List.of(HEXMOD_RESOURCES, HEXMOD_GENERATED)),
                "hexcasting", "thehexbook", "en_us");
        BookLayout.Entry entry = layout.entries().get("hexcasting:greatwork/brainsweeping");
        assertNotNull(entry, "the brainsweeping entry is not in the book");

        Map<String, String> lang = Json5.flatten(Files.readString(HEXMOD_LANG, StandardCharsets.UTF_8));
        int index = entry.pages().size() - 1;
        Map<?, ?> page = (Map<?, ?>) entry.pages().get(index);
        ConversionReport report = ConversionReport.empty();
        String markdown = PageTypeRegistry.standard().render(new PageRenderContext(
                layout, entry, index, page, lang, new BookTextConverter(layout, report)));

        assertTrue(markdown.contains("<entity id=\"minecraft:villager\""), markdown);
        assertTrue(markdown.contains("<item id=\"hexcasting:charged_amethyst\" count=\"10\""), markdown);
        assertTrue(markdown.contains("any sort of villager will do"), markdown);
        assertFalse(markdown.contains("<pta:page"), markdown);
        assertTrue(report.hostedPageTypes().isEmpty(), report.toMarkdown());
    }

    private static String cost(long media) {
        return """
                { type: "hexcasting:brainsweep",
                  blockIn: { type: "hexcasting:block", block: "minecraft:amethyst_block" },
                  cost: %d,
                  entityIn: { type: "hexcasting:villager", minLevel: 3 },
                  result: { Name: "minecraft:budding_amethyst" } }
                """.formatted(media);
    }

    private static int occurrences(String haystack, String needle) {
        return haystack.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }
}
