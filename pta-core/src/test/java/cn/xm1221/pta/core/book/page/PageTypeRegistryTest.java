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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageTypeRegistryTest {
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

    private static PageRenderContext context(BookLayout layout, Map<String, Object> page) {
        return context(layout, page, ConversionReport.empty());
    }

    @Test
    void unregisteredPageTypesAreHandedToPatchouli() {
        BookLayout layout = demoBook();
        ConversionReport report = ConversionReport.empty();

        String markdown = PageTypeRegistry.standard()
                .render(context(layout, Map.of("type", "hexcasting:pattern"), report));

        assertTrue(markdown.contains(
                        "<pta:page book=\"demo:demo\" entry=\"item\" page=\"0\"/>"),
                markdown);
        assertTrue(report.hostedPageTypes().containsKey("hexcasting:pattern"),
                "hosting a page type has to be visible in the report: " + report.toMarkdown());
    }

    @Test
    void aRegisteredRendererTakesPrecedenceOverHosting() {
        PageTypeRegistry registry = PageTypeRegistry.standard()
                .register("hexcasting:pattern", context -> "rendered natively");
        BookLayout layout = demoBook();

        assertEquals("rendered natively",
                registry.render(context(layout, Map.of("type", "hexcasting:pattern"))));
    }

    /**
     * Declining is how a renderer says "not one of the ones I really handle" without having to know
     * what should happen instead.
     */
    @Test
    void aDecliningRendererFallsBackToHosting() {
        PageTypeRegistry registry = PageTypeRegistry.standard()
                .register("hexcasting:pattern", context -> null);
        BookLayout layout = demoBook();

        String markdown = registry.render(context(layout, Map.of("type", "hexcasting:pattern")));
        assertTrue(markdown.contains("<pta:page"), markdown);
    }

    @Test
    void theFallbackCanBeReplaced() {
        PageTypeRegistry registry = PageTypeRegistry.standard().fallback(context -> "nothing here");
        BookLayout layout = demoBook();

        assertEquals("nothing here", registry.render(context(layout, Map.of("type", "whatever:x"))));
    }

    /** Patchouli lets a book write "text" for "patchouli:text", and so must the lookup. */
    @Test
    void typesAreQualifiedOnBothSides() {
        BookLayout layout = demoBook();
        PageTypeRegistry registry = PageTypeRegistry.standard();

        assertEquals("patchouli:text", context(layout, Map.of()).type());
        assertEquals("patchouli:text", context(layout, Map.of("type", "text")).type());
        assertSame(registry.rendererFor("text"), registry.rendererFor("patchouli:text"));
        assertNotNull(registry.rendererFor("text"));
    }

    @Test
    void textPagesAreConvertedRatherThanHosted() {
        BookLayout layout = demoBook();

        String markdown = PageTypeRegistry.standard().render(context(layout,
                Map.of("type", "patchouli:text", "text", "hello $(#ff0000)world$()")));

        assertTrue(markdown.contains("<color=#ff0000>world</color>"), markdown);
        assertFalse(markdown.contains("<pta:page"), markdown);
    }

    @Test
    void textPageTitlesBecomeHeadings() {
        BookLayout layout = demoBook();

        String markdown = PageTypeRegistry.standard().render(context(layout,
                Map.of("type", "patchouli:text", "title", "A Heading", "text", "body")));

        assertTrue(markdown.startsWith("## A Heading\n\n"), markdown);
        assertTrue(markdown.contains("body"), markdown);
    }

    @Test
    void linkPagesKeepTheTextAndAddTheTarget() {
        BookLayout layout = demoBook();

        String markdown = PageTypeRegistry.standard().render(context(layout, Map.of(
                "type", "patchouli:link",
                "text", "see the site",
                "url", "https://example.com",
                "link_text", "Example")));

        assertTrue(markdown.contains("see the site"), markdown);
        assertTrue(markdown.contains("[Example](https://example.com)"), markdown);
    }

    /** The registry must be usable without touching any of the built-ins. */
    @Test
    void anEmptyRegistryUsesOnlyItsFallback() {
        PageTypeRegistry registry = new PageTypeRegistry().fallback(context -> "fallback");
        BookLayout layout = demoBook();

        assertEquals("fallback", registry.render(context(layout, Map.of("type", "patchouli:text"))));
    }
}
