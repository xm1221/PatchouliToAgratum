package cn.xm1221.pta.core.book.page;

import cn.xm1221.pta.core.book.BookConverter;
import cn.xm1221.pta.core.book.BookLayout;
import cn.xm1221.pta.core.book.BookSource;
import cn.xm1221.pta.core.book.BookTextConverter;
import cn.xm1221.pta.core.report.ConversionReport;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The page types besides text, recipes and Hex Casting's patterns.
 *
 * <p>These are the small ones — a spotlight is an item with prose, an image is a texture, an empty
 * page is nothing — so the tests are about the two ways each can go wrong: a page that should be
 * converted being hosted anyway, and a page that cannot be expressed faithfully being converted
 * into something wrong. The second is the dangerous one, which is why every renderer declines
 * rather than approximates.</p>
 */
class BuiltinPageRendererTest {
    private static final Path HEXMOD_RESOURCES = Path.of("E:/miemod/libs/HexMod/Common/src/main/resources");

    private static BookLayout demoBook() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("data/demo/patchouli_books/demo/book.json", "{ name: \"Demo\", i18n: false }");
        files.put("assets/demo/patchouli_books/demo/en_us/categories/demo.json",
                "{ name: \"Demo\", description: \"d\", icon: \"minecraft:book\" }");
        files.put("assets/demo/patchouli_books/demo/en_us/entries/item.json",
                "{ name: \"Item\", icon: \"minecraft:diamond\", category: \"demo:demo\", pages: [] }");
        return BookLayout.load(BookSource.of(files), "demo", "demo", "en_us");
    }

    private static PageRenderContext context(BookLayout layout, Map<String, Object> page,
                                             ConversionReport report) {
        return new PageRenderContext(layout, layout.entries().get("demo:item"), 0, page, Map.of(),
                new BookTextConverter(layout, report));
    }

    private static String render(Map<String, Object> page) {
        return PageTypeRegistry.standard().render(context(demoBook(), page, ConversionReport.empty()));
    }

    private static String render(BookLayout layout, Map<String, Object> page, ConversionReport report) {
        return PageTypeRegistry.standard().render(context(layout, page, report));
    }

    @Test
    void aSpotlightBecomesANativeItem() {
        String markdown = render(Map.of(
                "type", "patchouli:spotlight",
                "item", "minecraft:oak_sapling",
                "link_recipe", true));

        assertTrue(markdown.contains("<item id=\"minecraft:oak_sapling\"/>"), markdown);
        assertFalse(markdown.contains("<pta:page"), markdown);
    }

    @Test
    void aSpotlightWithATitleShowsItAndKeepsTheItemNameOut() {
        String markdown = render(Map.of(
                "type", "patchouli:spotlight",
                "item", "minecraft:potion",
                "title", "Three Potions"));

        assertTrue(markdown.startsWith("## Three Potions"), markdown);
        assertTrue(markdown.contains("<item id=\"minecraft:potion\" showText=\"false\"/>"), markdown);
    }

    @Test
    void severalItemsAreLaidOutInARow() {
        String markdown = render(Map.of(
                "type", "patchouli:spotlight",
                "item", "minecraft:oak_log, minecraft:birch_log"));

        assertTrue(markdown.contains("<row>"), markdown);
        assertTrue(markdown.contains("<item id=\"minecraft:oak_log\"/>"), markdown);
        assertTrue(markdown.contains("<item id=\"minecraft:birch_log\"/>"), markdown);
    }

    @Test
    void aSpotlightWithItemComponentsIsHostedInstead() {
        ConversionReport report = ConversionReport.empty();
        String markdown = render(demoBook(), Map.of(
                "type", "patchouli:spotlight",
                "item", "hexcasting:battery[hexcasting:media=640000]"), report);

        assertTrue(markdown.contains("<pta:page"), markdown);
        assertEquals(Map.of("patchouli:spotlight", 1), report.hostedPageTypes());
    }

    @Test
    void aSpotlightOfATagIsHostedInstead() {
        String markdown = render(Map.of(
                "type", "patchouli:spotlight",
                "item", "#minecraft:logs"));

        assertTrue(markdown.contains("<pta:page"), markdown);
    }

    @Test
    void anImageBecomesAMarkdownImage() {
        String markdown = render(Map.of(
                "type", "patchouli:image",
                "images", java.util.List.of("hexcasting:textures/gui/entries/spell_circle.png"),
                "border", true,
                "title", "Teleport Circle"));

        assertTrue(markdown.startsWith("## Teleport Circle"), markdown);
        assertTrue(markdown.contains("![Teleport Circle](hexcasting:gui/entries/spell_circle.png)"), markdown);
        assertFalse(markdown.contains("<pta:page"), markdown);
    }

    @Test
    void anImageThatIsNotAPngIsHostedInstead() {
        String markdown = render(Map.of(
                "type", "patchouli:image",
                "images", java.util.List.of("demo:textures/gui/diagram.svg")));

        assertTrue(markdown.contains("<pta:page"), markdown);
    }

    @Test
    void anEmptyPageBecomesNothing() {
        String markdown = render(Map.of("type", "patchouli:empty"));

        assertEquals("", markdown);
    }

    @Test
    void anEntityPageBecomesANativeEntity() {
        String markdown = render(Map.of(
                "type", "patchouli:entity",
                "entity", "minecraft:creeper"));

        assertTrue(markdown.contains("<entity id=\"minecraft:creeper\"/>"), markdown);
        assertFalse(markdown.contains("<pta:page"), markdown);
    }

    /** NBT written into the entity id is Patchouli's spelling, not Ageratum's, so it declines. */
    @Test
    void anEntityWithNbtIsHostedInstead() {
        String markdown = render(Map.of(
                "type", "patchouli:entity",
                "entity", "minecraft:creeper{powered:1}"));

        assertTrue(markdown.contains("<pta:page"), markdown);
    }

    /**
     * The whole book, so the aggregate is pinned rather than one page at a time: what is left to
     * Patchouli is Hex Casting's own custom page type plus the spotlights that carry item
     * components, and none of the page types this stage converted.
     */
    @Test
    void onlyPagesThatCannotBeExpressedAreLeftToPatchouli() {
        Assumptions.assumeTrue(Files.isDirectory(HEXMOD_RESOURCES), "HexMod checkout not present");

        BookLayout layout = BookLayout.load(
                BookSource.ofDirectory(HEXMOD_RESOURCES, "hexcasting", "thehexbook"),
                "hexcasting", "thehexbook", "en_us");
        Map<String, Integer> hosted = BookConverter.convert(layout, Map.of()).report().hostedPageTypes();

        assertEquals(Map.of("hexcasting:brainsweep", 8, "patchouli:spotlight", 4), hosted, hosted.toString());
    }
}
