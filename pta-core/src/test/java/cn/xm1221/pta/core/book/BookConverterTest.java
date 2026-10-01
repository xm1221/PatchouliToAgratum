package cn.xm1221.pta.core.book;

import cn.xm1221.pta.core.lang.Json5;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

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

class BookConverterTest {
    private static final Path HEXMOD_RESOURCES = Path.of("E:/miemod/libs/HexMod/Common/src/main/resources");
    private static final Path HEXMOD_LANG = HEXMOD_RESOURCES.resolve(
            "assets/hexcasting/lang/en_us.flatten.json5");

    private static BookConverter.Output convertHexmod() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(HEXMOD_RESOURCES)
                && Files.isRegularFile(HEXMOD_LANG), "HexMod checkout not present");
        BookLayout layout = BookLayout.load(
                BookSource.ofDirectory(HEXMOD_RESOURCES, "hexcasting", "thehexbook"),
                "hexcasting", "thehexbook", "en_us");
        Map<String, String> lang = Json5.flatten(Files.readString(HEXMOD_LANG, StandardCharsets.UTF_8));
        return BookConverter.convert(layout, lang);
    }

    @Test
    void convertsTheWholeBook() throws IOException {
        BookConverter.Output output = convertHexmod();

        // One document per category index plus one per entry.
        assertEquals(9 + 83, output.documents().size());
        assertEquals(83, output.report().documents());

        for (Map.Entry<String, String> document : output.documents().entrySet()) {
            String text = document.getValue();
            assertTrue(text.startsWith("---\n"), document.getKey() + " has no front matter");
            assertTrue(text.contains("\ntitle: \""), document.getKey() + " has no title");
            assertTrue(text.length() > 40, document.getKey() + " looks empty");
        }
    }

    @Test
    void writesCategoryIndexDocuments() throws IOException {
        BookConverter.Output output = convertHexmod();
        String items = output.documents().get("items/index");
        assertNotNull(items, "categories must become directories with an index document");
        assertTrue(items.contains("title: \"Items\""), items);
        assertTrue(items.contains("# Items"), items);
    }

    @Test
    void writesEntryDocumentsWithFrontMatter() throws IOException {
        BookConverter.Output output = convertHexmod();
        String amethyst = output.documents().get("items/amethyst");
        assertNotNull(amethyst);
        // The entry icon becomes an item binding, which gives Ageratum's ponder behaviour.
        assertTrue(amethyst.contains("items: \"minecraft:amethyst_shard\""), amethyst);
        assertTrue(amethyst.contains("title: \"Amethyst\""), amethyst);
        assertTrue(amethyst.contains("pta_lock: \"hexcasting:root\""), amethyst);
    }

    @Test
    void convertsTextPagesAndHandsOtherPagesToTheComponent() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("data/demo/patchouli_books/demo/book.json", "{ name: \"Demo\" }");
        files.put("assets/demo/patchouli_books/demo/en_us/categories/demo.json",
                "{ name: \"Demo\", description: \"about\", icon: \"minecraft:book\" }");
        files.put("assets/demo/patchouli_books/demo/en_us/entries/demo/item.json", """
                {
                  name: "An Item",
                  icon: "minecraft:diamond",
                  category: "demo:demo",
                  advancement: "minecraft:story/root",
                  pages: [
                    { type: "patchouli:text", text: "First $(#ff0000)red$() page." },
                    { type: "patchouli:spotlight", item: "minecraft:diamond", title: "Look" },
                    { type: "patchouli:text", title: "Heading", text: "Second page." }
                  ]
                }
                """);

        BookLayout layout = BookLayout.load(BookSource.of(files), "demo", "demo", "en_us");
        BookConverter.Output output = BookConverter.convert(layout, Map.of());
        String document = output.documents().get("demo/item");

        // Text pages become real Markdown.
        assertTrue(document.contains("First <color=#ff0000>red</color> page."), document);
        // Pages that draw something are handed to the runtime component.
        assertTrue(document.contains("<pta:page book=\"demo:demo\" entry=\"demo/item\" page=\"1\"/>"),
                document);
        // A page title becomes a section heading.
        assertTrue(document.contains("## Heading"), document);
        // The lock travels in the front matter.
        assertTrue(document.contains("pta_lock: \"minecraft:story/root\""), document);
        assertTrue(output.report().unsupportedPageTypes().containsKey("patchouli:spotlight"),
                output.report().toString());
    }

    @Test
    void dropsTextureIconsRatherThanBindingThem() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("data/demo/patchouli_books/demo/book.json", "{ name: \"Demo\" }");
        files.put("assets/demo/patchouli_books/demo/en_us/categories/demo.json",
                "{ name: \"Demo\", description: \"d\", icon: \"minecraft:book\" }");
        files.put("assets/demo/patchouli_books/demo/en_us/entries/demo/item.json",
                "{ name: \"T\", icon: \"demo:textures/gui/thing.png\", category: \"demo:demo\","
                        + " pages: [ { type: \"patchouli:text\", text: \"x\" } ] }");

        BookLayout layout = BookLayout.load(BookSource.of(files), "demo", "demo", "en_us");
        String document = BookConverter.convert(layout, Map.of()).documents().get("demo/item");
        assertFalse(document.contains("items:"), "a texture path is not an item id: " + document);
    }

    /**
     * Every generated link has to point at a document that was actually written.
     */
    @Test
    void everyGeneratedLinkTargetsAGeneratedDocument() throws IOException {
        BookConverter.Output output = convertHexmod();
        List<String> dangling = new ArrayList<>();
        int links = 0;

        for (Map.Entry<String, String> document : output.documents().entrySet()) {
            String from = document.getKey();
            String text = document.getValue();
            int index = text.indexOf("](");
            while (index >= 0) {
                int end = text.indexOf(')', index);
                if (end < 0) {
                    break;
                }
                String target = text.substring(index + 2, end);
                index = text.indexOf("](", end);
                if (target.startsWith("http")) {
                    continue;
                }
                links++;
                String resolved = resolveRelative(from, target);
                if (!output.documents().containsKey(resolved)) {
                    dangling.add(from + " -> " + target + " (resolved " + resolved + ")");
                }
            }
        }

        // Only text pages are converted here; the text of non-text pages is rendered by
        // Patchouli through <pta:page>, so it does not pass through the link rewriter.
        assertTrue(links > 150, "expected the book's text-page links, got " + links);
        assertTrue(dangling.isEmpty(), "dangling links: " + dangling);
    }

    /** Mirrors how Ageratum resolves a relative target against the current document's directory. */
    private static String resolveRelative(String fromDocument, String target) {
        String directory = BookLayout.directoryOf(fromDocument);
        List<String> parts = new ArrayList<>();
        if (!directory.isEmpty()) {
            parts.addAll(List.of(directory.split("/")));
        }
        for (String segment : target.split("/")) {
            if (segment.equals("..")) {
                if (!parts.isEmpty()) {
                    parts.remove(parts.size() - 1);
                }
            } else if (!segment.isEmpty() && !segment.equals(".")) {
                parts.add(segment);
            }
        }
        return String.join("/", parts);
    }

    @Test
    void reportsLanguageKeysThatGoNowhere() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("data/demo/patchouli_books/demo/book.json", "{ name: \"Demo\", i18n: true }");
        files.put("assets/demo/patchouli_books/demo/en_us/categories/demo.json",
                "{ name: \"demo.category\", description: \"demo.category.desc\","
                        + " icon: \"minecraft:book\" }");
        files.put("assets/demo/patchouli_books/demo/en_us/entries/demo/item.json",
                "{ name: \"demo.entry\", icon: \"minecraft:diamond\", category: \"demo:demo\","
                        + " pages: [ { type: \"patchouli:text\", text: \"demo.page.missing\" } ] }");

        BookLayout layout = BookLayout.load(BookSource.of(files), "demo", "demo", "en_us");
        BookConverter.Output output = BookConverter.convert(layout, Map.of("demo.entry", "Resolved"));
        String document = output.documents().get("demo/item");

        assertTrue(document.contains("title: \"Resolved\""), document);
        // The key is left in place rather than dropped, and reported.
        assertTrue(document.contains("demo.page.missing"), document);
        assertTrue(output.report().unrenderedTextFields().containsKey("demo.page.missing"),
                output.report().toString());
    }
}
