package cn.xm1221.pta.core.book.page;

import cn.xm1221.pta.core.book.BookLayout;
import cn.xm1221.pta.core.book.BookSource;
import cn.xm1221.pta.core.book.BookTextConverter;
import cn.xm1221.pta.core.report.ConversionReport;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The recipe family is the first page type family converted to Ageratum's own components rather
 * than hosted, so these tests pin both halves of that promise: the recipe becomes a native
 * {@code <recipe>} component, and the page's prose stays real Markdown.
 */
class RecipePageRendererTest {
    private static BookLayout demoBook() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("data/demo/patchouli_books/demo/book.json", "{ name: \"Demo\" }");
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

    private static int occurrences(String haystack, String needle) {
        return haystack.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }

    @Test
    void aCraftingPageBecomesANativeRecipe() {
        String markdown = render(Map.of("type", "patchouli:crafting", "recipe", "minecraft:chest"));

        assertTrue(markdown.contains("<recipe id=\"minecraft:chest\"/>"), markdown);
        assertFalse(markdown.contains("<pta:page"), markdown);
    }

    @Test
    void aSecondRecipeIsLaidOutBesideTheFirst() {
        String markdown = render(Map.of(
                "type", "patchouli:crafting",
                "recipe", "minecraft:chest",
                "recipe2", "minecraft:barrel"));

        assertTrue(markdown.contains("<row>"), markdown);
        assertTrue(markdown.contains("---"), markdown);
        assertEquals(2, occurrences(markdown, "<recipe id="), markdown);
        assertTrue(markdown.indexOf("minecraft:chest") < markdown.indexOf("minecraft:barrel"),
                markdown);
    }

    /** The prose under a recipe is the whole reason a page is worth converting. */
    @Test
    void thePageTextBecomesMarkdownAfterTheRecipe() {
        String markdown = render(Map.of(
                "type", "patchouli:crafting",
                "recipe", "minecraft:chest",
                "text", "Crafted from planks, needing $(bold)a lot$() of them."));

        assertTrue(markdown.contains("Crafted from planks, needing **a lot** of them."), markdown);
        assertTrue(markdown.indexOf("<recipe id=") < markdown.indexOf("Crafted from"),
                markdown);
    }

    @Test
    void thePageTitleBecomesAHeading() {
        String markdown = render(Map.of(
                "type", "patchouli:smelting",
                "recipe", "minecraft:glass",
                "title", "Smelting",
                "text", "body"));

        assertTrue(markdown.startsWith("## Smelting\n\n"), markdown);
    }

    /**
     * Without an id there is nothing to draw natively, so the page must go back to Patchouli
     * instead of turning into an empty section.
     */
    @Test
    void aRecipePageWithoutAnIdIsHosted() {
        BookLayout layout = demoBook();
        ConversionReport report = ConversionReport.empty();

        String markdown = PageTypeRegistry.standard()
                .render(context(layout, Map.of("type", "patchouli:crafting"), report));

        assertTrue(markdown.contains("<pta:page"), markdown);
        assertTrue(report.hostedPageTypes().containsKey("patchouli:crafting"),
                report.toMarkdown());
    }

    /** Every recipe page type Patchouli ships has to be answered, not just crafting. */
    @Test
    void everyPatchouliRecipeTypeIsConverted() {
        PageTypeRegistry registry = PageTypeRegistry.standard();
        for (String type : RecipePageRenderer.TYPES) {
            assertNotNull(registry.rendererFor(type), type + " has no renderer");

            String markdown = registry.render(context(demoBook(),
                    Map.of("type", type, "recipe", "minecraft:stone"), ConversionReport.empty()));
            assertTrue(markdown.contains("<recipe id=\"minecraft:stone\"/>"),
                    type + " did not become a recipe: " + markdown);
        }
    }

    /**
     * Hex Casting's merged-recipe page: one grid standing in for a dozen variants. Expanding it
     * per variant is a deliberate presentation change, and it has to be reported.
     */
    @Test
    void mergedRecipesAreExpandedIntoOnePerVariant() {
        BookLayout layout = demoBook();
        ConversionReport report = ConversionReport.empty();

        String markdown = PageTypeRegistry.standard().render(context(layout, Map.of(
                "type", "hexcasting:crafting_multi",
                "heading", "Any dye will do",
                "text", "Colour it however you like.",
                "recipes", java.util.List.of("demo:red", "demo:green", "demo:blue")), report));

        assertTrue(markdown.startsWith("## Any dye will do\n\n"), markdown);
        assertEquals(3, occurrences(markdown, "<recipe id="), markdown);
        assertTrue(markdown.contains("<recipe id=\"demo:blue\"/>"), markdown);
        assertTrue(markdown.contains("Colour it however you like."), markdown);
        assertFalse(report.multiRecipePages().isEmpty(),
                "expanding a merged recipe has to be visible in the report");
    }

    @Test
    void aMergedRecipePageWithoutRecipesIsHosted() {
        String markdown = render(Map.of("type", "hexcasting:crafting_multi", "heading", "Nothing"));

        assertTrue(markdown.contains("<pta:page"), markdown);
    }
}
