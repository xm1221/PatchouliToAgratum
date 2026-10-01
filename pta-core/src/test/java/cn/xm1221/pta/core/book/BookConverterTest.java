package cn.xm1221.pta.core.book;

import cn.xm1221.pta.core.lang.Json5;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

        // One root document, one per category index, and one per entry.
        assertEquals(1 + 9 + 83, output.documents().size());
        assertEquals(83, output.report().documents());

        for (Map.Entry<String, String> document : output.documents().entrySet()) {
            String text = document.getValue();
            assertTrue(text.startsWith("---\n"), document.getKey() + " has no front matter");
            assertTrue(text.contains("\ntitle: \""), document.getKey() + " has no title");
            assertTrue(text.length() > 40, document.getKey() + " looks empty");
        }
    }

    /**
     * Ageratum's command opens {@code ageratum/<language>/index.md} and nothing else, so a book
     * without a root document is unreachable even when every one of its documents loaded.
     */
    @Test
    void writesTheRootDocumentTheCommandOpens() throws IOException {
        BookConverter.Output output = convertHexmod();
        String root = output.documents().get(BookConverter.ROOT_DOCUMENT);
        assertNotNull(root, "no root document, so /ageratum would report 'file not found'");
        assertEquals("Hex Notebook", titleOf(root));
        assertTrue(root.contains("I seem to have discovered a new method"), root);
        // The landing page links to each root category, and not to nested ones.
        assertTrue(root.contains("](items/index)"), root);
        assertTrue(root.contains("](patterns/index)"), root);
        assertFalse(root.contains("great_spells/index"), "a nested category is not a root one");
    }

    private static String titleOf(String document) {
        int start = document.indexOf("title: \"") + "title: \"".length();
        int end = document.indexOf('"', start);
        return document.substring(start, end);
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
                    { type: "patchouli:multiblock", name: "Stairs" },
                    { type: "patchouli:text", title: "Heading", text: "Second page." }
                  ]
                }
                """);

        BookLayout layout = BookLayout.load(BookSource.of(files), "demo", "demo", "en_us");
        BookConverter.Output output = BookConverter.convert(layout, Map.of());
        String document = output.documents().get("demo/item");

        // Text pages become real Markdown.
        assertTrue(document.contains("First <color=#ff0000>red</color> page."), document);
        // A spotlight becomes a native item, title and all.
        assertTrue(document.contains("## Look"), document);
        assertTrue(document.contains("<item id=\"minecraft:diamond\" showText=\"false\"/>"), document);
        // A page type with no native equivalent is handed to the runtime component.
        assertTrue(document.contains("<pta:page book=\"demo:demo\" entry=\"demo/item\" page=\"2\"/>"),
                document);
        // A page title becomes a section heading.
        assertTrue(document.contains("## Heading"), document);
        // The lock travels in the front matter.
        assertTrue(document.contains("pta_lock: \"minecraft:story/root\""), document);
        assertTrue(output.report().hostedPageTypes().containsKey("patchouli:multiblock"),
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
                int bracket = text.lastIndexOf('[', index);
                boolean image = bracket > 0 && text.charAt(bracket - 1) == '!';
                index = text.indexOf("](", end);
                if (target.startsWith("http") || image) {
                    // An image names a texture in a resource pack, not a document in this guide, so
                    // it is not a link this test can resolve against the generated files.
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

    /**
     * Patchouli lets a {@code pages} element be a bare string, meaning a text page with that text.
     * Every lore entry in Hex Casting is written that way, so skipping non-object pages produced
     * chapters that were nothing but a title.
     */
    @Test
    void convertsBareStringPages() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("data/demo/patchouli_books/demo/book.json", "{ name: \"Demo\" }");
        files.put("assets/demo/patchouli_books/demo/en_us/categories/demo.json",
                "{ name: \"Demo\", description: \"d\", icon: \"minecraft:book\" }");
        files.put("assets/demo/patchouli_books/demo/en_us/entries/demo/story.json", """
                {
                  name: "A Story",
                  icon: "minecraft:book",
                  category: "demo:demo",
                  pages: [ "first page of the story", { "type": "patchouli:text", "text": "second page" } ]
                }
                """);

        BookLayout layout = BookLayout.load(BookSource.of(files), "demo", "demo", "en_us");
        String document = BookConverter.convert(layout, Map.of()).documents().get("demo/story");

        assertTrue(document.contains("first page of the story"), document);
        assertTrue(document.contains("second page"), document);
    }

    /** The regression that made the lore chapter a title with nothing under it. */
    @Test
    void givesTheLoreChapterRealContent() throws IOException {
        BookConverter.Output output = convertHexmod();
        String cardamom = output.documents().get("lore/cardamom1");
        assertNotNull(cardamom);
        // Body, not just the heading: the file must be longer than its own front matter + title.
        int bodyStart = cardamom.indexOf('\n', cardamom.indexOf("# "));
        assertTrue(cardamom.length() - bodyStart > 400,
                "lore entry has almost no body: " + cardamom.substring(0, Math.min(300, cardamom.length())));
        assertFalse(cardamom.contains("hexcasting.page.lore.cardamom1.1"),
                "the language key was not resolved");
    }

    /** A chapter index lists its own entries, which is the only grouped view a reader gets. */
    @Test
    void writesAChapterContentsList() throws IOException {
        BookConverter.Output output = convertHexmod();

        String patterns = output.documents().get("patterns/index");
        assertNotNull(patterns);
        assertTrue(patterns.contains("- [Basic Patterns](basics)"), patterns);
        assertTrue(patterns.contains("](readwrite)"), patterns);
        // The nested category's entries belong to that category, not to this one.
        assertFalse(patterns.contains("great_spells__"), patterns);

        // A nested chapter's entries live in the parent directory, so the link climbs out of the
        // chapter's own directory first. Every such link is verified against the document map by
        // everyGeneratedLinkTargetsAGeneratedDocument.
        // The nested chapter now lives in the parent directory alongside its own entries, so the
        // link is direct rather than climbing out of a subdirectory.
        String greatSpells = output.documents().get("patterns/great_spells__index");
        assertNotNull(greatSpells);
        assertTrue(greatSpells.contains("](great_spells__altiora)"), greatSpells);
    }

    /**
     * Ageratum renders a child directory's index in a pass that runs after the parent's own
     * documents, so a sub-chapter would always end up below every entry of its parent. Flattening
     * the nested index into the parent directory turns its position into front matter weight.
     */
    @Test
    void subChaptersSortBeforeTheirParentsEntries() throws IOException {
        BookConverter.Output output = convertHexmod();

        // spells has sortnum 0, great_spells has sortnum 1; both stay negative so they precede the
        // parent's unweighted entries, and their order still follows Patchouli's sortnum.
        assertTrue(output.documents().get("patterns/spells__index").contains("weight: -1000"),
                output.documents().get("patterns/spells__index"));
        assertTrue(output.documents().get("patterns/great_spells__index").contains("weight: -999"),
                output.documents().get("patterns/great_spells__index"));
        assertFalse(output.documents().get("patterns/index").contains("weight:"),
                "a root chapter's position is not decided by weight");

        // Reproduce Ageratum's ordering exactly: weight, then file name.
        List<String> sidebar = output.documents().entrySet().stream()
                .filter(entry -> BookLayout.directoryOf(entry.getKey()).equals("patterns"))
                .filter(entry -> !entry.getKey().equals("patterns/index"))
                .sorted(Comparator
                        .comparingInt((Map.Entry<String, String> entry) -> weightOf(entry.getValue()))
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .toList();

        assertEquals("patterns/spells__index", sidebar.get(0), sidebar.toString());
        assertEquals("patterns/great_spells__index", sidebar.get(1), sidebar.toString());
        // ...and the parent's own entries follow.
        assertEquals("patterns/advanced_escaping", sidebar.get(2), sidebar.toString());
    }

    /** The sidebar's sort key, read back out of the generated front matter. */
    private static int weightOf(String document) {
        Matcher matcher = Pattern.compile("(?m)^weight:\\s*(-?\\d+)$").matcher(document);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
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
