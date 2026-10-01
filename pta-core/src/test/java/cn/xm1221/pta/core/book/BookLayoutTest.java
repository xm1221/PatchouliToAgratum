package cn.xm1221.pta.core.book;

import cn.xm1221.pta.core.lang.Json5;
import cn.xm1221.pta.core.text.AgeratumTextWriter;
import cn.xm1221.pta.core.text.MacroExpander;
import cn.xm1221.pta.core.text.PatchouliTextScanner;
import cn.xm1221.pta.core.text.TextCommand;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookLayoutTest {
    private static final Path HEXMOD_RESOURCES = Path.of("E:/miemod/libs/HexMod/Common/src/main/resources");
    private static final Path HEXMOD_LANG = HEXMOD_RESOURCES.resolve(
            "assets/hexcasting/lang/en_us.flatten.json5");

    private static BookLayout hexmodBook() {
        Assumptions.assumeTrue(Files.isDirectory(HEXMOD_RESOURCES), "HexMod checkout not present");
        return BookLayout.load(
                BookSource.ofDirectory(HEXMOD_RESOURCES, "hexcasting", "thehexbook"),
                "hexcasting", "thehexbook", "en_us");
    }

    @Test
    void loadsTheRealHexModBook() {
        BookLayout layout = hexmodBook();

        assertEquals("hexcasting:thehexbook", layout.bookId());
        assertEquals("en_us", layout.language());
        assertTrue(layout.usesI18n(), "Hex Casting stores page text as language keys");
        assertEquals(11, layout.macros().size());

        assertEquals(9, layout.categories().size());
        assertEquals(83, layout.entries().size());

        // A top-level category and a nested one.
        BookLayout.Category items = layout.categories().get("hexcasting:items");
        assertNotNull(items);
        assertEquals("items", items.directory());
        assertEquals("items/index", items.indexPath());
        assertNull(items.parent());

        BookLayout.Category greatSpells = layout.categories().get("hexcasting:patterns/great_spells");
        assertNotNull(greatSpells);
        assertEquals("patterns/great_spells", greatSpells.directory());
        assertEquals("patterns/great_spells/index", greatSpells.indexPath());
        assertEquals("hexcasting:patterns", greatSpells.parent());
    }

    @Test
    void mapsEntriesOntoDocuments() {
        BookLayout layout = hexmodBook();

        BookLayout.Entry amethyst = layout.entries().get("hexcasting:items/amethyst");
        assertNotNull(amethyst);
        assertEquals("items/amethyst", amethyst.documentPath());
        assertEquals("hexcasting:items", amethyst.category());
        assertTrue(amethyst.pages().size() > 1, "expected several pages");

        // Nested categories put their entries one level deeper.
        assertNotNull(layout.entries().get("hexcasting:patterns/patterns_as_iotas"));

        // Every entry path must be unique, or two documents would be written to one file.
        List<String> paths = new ArrayList<>();
        layout.entries().values().forEach(entry -> paths.add(entry.documentPath()));
        assertEquals(paths.size(), paths.stream().distinct().count(), "duplicate document paths");
    }

    @Test
    void resolvesEntryAndCategoryTargets() {
        BookLayout layout = hexmodBook();

        assertEquals("items/amethyst", layout.resolveTarget("items/amethyst"));
        assertEquals("items/amethyst", layout.resolveTarget("hexcasting:items/amethyst"));
        assertEquals("items/index", layout.resolveTarget("hexcasting:items"));
        assertEquals("patterns/great_spells/index", layout.resolveTarget("patterns/great_spells"));
        assertNull(layout.resolveTarget("no/such/entry"));
        assertNull(layout.resolveTarget(""));
    }

    @Test
    void rewritesTargetsRelativeToTheLinkingDocument() {
        // Same directory: no traversal needed.
        assertEquals("dust", BookLayout.relativize("items/amethyst", "items/dust"));
        // Sibling directory one level up.
        assertEquals("../patterns/basics", BookLayout.relativize("items/amethyst", "patterns/basics"));
        // Into a nested directory without leaving the parent: only the tail differs.
        assertEquals("great_spells/index",
                BookLayout.relativize("patterns/basics", "patterns/great_spells/index"));
        // Out of a nested directory into a sibling of its parent.
        assertEquals("../../items/amethyst",
                BookLayout.relativize("patterns/great_spells/foo", "items/amethyst"));
        // From a top-level document.
        assertEquals("items/amethyst", BookLayout.relativize("index", "items/amethyst"));
        // To a top-level document.
        assertEquals("../index", BookLayout.relativize("items/amethyst", "index"));
    }

    /**
     * The acceptance criterion for this stage: every internal link in the real book resolves to
     * a document the converter will actually write.
     */
    @Test
    void resolvesEveryLinkInTheRealBook() throws IOException {
        BookLayout layout = hexmodBook();
        Assumptions.assumeTrue(Files.isRegularFile(HEXMOD_LANG), "HexMod language file not present");
        Map<String, String> lang = Json5.flatten(Files.readString(HEXMOD_LANG, StandardCharsets.UTF_8));

        MacroExpander expander = MacroExpander.of(layout.macros());
        List<String> unresolved = new ArrayList<>();
        int links = 0;

        for (BookLayout.Entry entry : layout.entries().values()) {
            for (Object page : entry.pages()) {
                if (!(page instanceof Map<?, ?> pageJson)) {
                    continue;
                }
                Object text = pageJson.get("text");
                if (!(text instanceof String raw) || raw.isBlank()) {
                    continue;
                }
                // With i18n on, the JSON holds a language key; without it, the text itself.
                String source = layout.usesI18n() ? lang.getOrDefault(raw, raw) : raw;

                AgeratumTextWriter.LinkResolver resolver = (target, anchor, external) -> {
                    if (external) {
                        return target;
                    }
                    String resolved = layout.resolveTarget(target);
                    if (resolved == null) {
                        unresolved.add(target);
                        return target;
                    }
                    String relative = BookLayout.relativize(entry.documentPath(), resolved);
                    return anchor == null ? relative : relative + "#" + anchor;
                };

                AgeratumTextWriter.Result result = AgeratumTextWriter.write(
                        PatchouliTextScanner.scan(expander.expand(source).orThrow()), resolver);
                links += countLinks(result.markdown());
            }
        }

        // 332 links live in entry page text. The book contains more link commands than that
        // overall, but the rest sit in template components and in fields this pass does not
        // render yet, so this count is about page text only.
        assertTrue(links > 300, "expected the book's page-text links, got " + links);
        assertTrue(unresolved.isEmpty(), "links that point nowhere: " + unresolved);
    }

    /** Counts Markdown link destinations in rendered output. */
    private static int countLinks(String markdown) {
        int count = 0;
        int index = markdown.indexOf("](");
        while (index >= 0) {
            count++;
            index = markdown.indexOf("](", index + 2);
        }
        return count;
    }

    @Test
    void readsABookFromAnInMemorySource() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("data/demo/patchouli_books/demo/book.json", "{ name: \"Demo\", i18n: false }");
        files.put("assets/demo/patchouli_books/demo/en_us/categories/demo.json",
                "{ name: \"Demo\", description: \"d\", icon: \"minecraft:book\" }");
        files.put("assets/demo/patchouli_books/demo/en_us/entries/hello.json",
                "{ name: \"Hello\", icon: \"minecraft:diamond\", category: \"demo:demo\","
                        + " pages: [ \"just a string page\" ] }");

        BookLayout layout = BookLayout.load(BookSource.of(files), "demo", "demo", "en_us");
        assertEquals(1, layout.categories().size());
        assertEquals(1, layout.entries().size());
        assertEquals("hello", layout.entries().get("demo:hello").documentPath());
        assertEquals("demo/index", layout.resolveTarget("demo:demo"));
    }

    @Test
    void rejectsABookWithoutBookJson() {
        try {
            BookLayout.load(BookSource.of(Map.of()), "demo", "demo", "en_us");
            throw new AssertionError("expected a failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("book.json"), expected.getMessage());
        }
    }

    /** Guards the empty-directory case in relativize: "".split("/") is [""], not []. */
    @Test
    void handlesDocumentsAtTheRoot() {
        assertEquals("", BookLayout.directoryOf("index"));
        assertEquals("a", BookLayout.directoryOf("a/index"));
        assertEquals("items/amethyst", BookLayout.relativize("index", "items/amethyst"));
    }

    @Test
    void scansCommandsFromPageJson() {
        // Guards the shape the corpus test relies on: pages are maps with a "text" entry.
        BookLayout layout = hexmodBook();
        BookLayout.Entry entry = layout.entries().get("hexcasting:items/amethyst");
        boolean foundText = entry.pages().stream()
                .anyMatch(page -> page instanceof Map<?, ?> map && map.get("text") instanceof String);
        assertTrue(foundText, "expected at least one page with text");
        List<TextCommand> commands = PatchouliTextScanner.scan("plain $(#fff)coloured$()");
        assertEquals(4, commands.size(), "text, colour, text, reset");
    }
}
