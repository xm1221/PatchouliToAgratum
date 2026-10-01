package cn.xm1221.pta.core.text;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgeratumTextWriterTest {
    private static final Path HEXMOD_BOOK_JSON = Path.of(
            "E:/miemod/libs/HexMod/Common/src/main/resources/data/hexcasting/patchouli_books/thehexbook/book.json");
    private static final Path HEXMOD_EN_US = Path.of(
            "E:/miemod/libs/HexMod/Common/src/main/resources/assets/hexcasting/lang/en_us.flatten.json5");

    // ------------------------------------------------------------------- scanning

    /**
     * The dispatch order that lets one letter mean two things. Patchouli looks up functions
     * before exact command names.
     */
    @Test
    void bareKIsObfuscatedWhileKWithAParameterIsAKeybind() {
        List<TextCommand> commands = PatchouliTextScanner.scan("$(k)vs$(k:use)");
        assertEquals(3, commands.size());
        assertEquals(new TextCommand.Format(TextCommand.Format.Kind.OBFUSCATED), commands.get(0));
        assertEquals("vs", ((TextCommand.Text) commands.get(1)).value());
        assertEquals(new TextCommand.Keybind("use"), commands.get(2));
    }

    @Test
    void parsesInternalExternalAndAnchoredLinks() {
        List<TextCommand> commands = PatchouliTextScanner.scan(
                "$(l:items/staff)a$(/l)$(l:https://example.com)b$(/l)$(l:patterns/basics#hexcasting:get_caster)c$(/l)");

        List<TextCommand.LinkStart> links = new ArrayList<>();
        for (TextCommand command : commands) {
            if (command instanceof TextCommand.LinkStart start) {
                links.add(start);
            }
        }
        assertEquals(3, links.size());
        assertEquals(new TextCommand.LinkStart("items/staff", null, false), links.get(0));
        assertEquals(new TextCommand.LinkStart("https://example.com", null, true), links.get(1));
        // The anchor is split off at the first '#', leaving the entry id intact.
        assertEquals(new TextCommand.LinkStart("patterns/basics", "hexcasting:get_caster", false), links.get(2));
    }

    @Test
    void normalisesLegacyAndShortHexColours() {
        assertEquals(new TextCommand.ColorCode('6'), PatchouliTextScanner.scan("$(6)").get(0));
        assertEquals(new TextCommand.Color("#ff00aa"), PatchouliTextScanner.scan("$(#f0a)").get(0));
        assertEquals(new TextCommand.Color("#ff00aa"), PatchouliTextScanner.scan("$(#FF00AA)").get(0));
    }

    @Test
    void parsesListDepthsBreaksAndResets() {
        assertEquals(new TextCommand.ListItem(1), PatchouliTextScanner.scan("$(li)").get(0));
        assertEquals(new TextCommand.ListItem(3), PatchouliTextScanner.scan("$(li3)").get(0));
        assertEquals(new TextCommand.LineBreak(1), PatchouliTextScanner.scan("$(br)").get(0));
        assertEquals(new TextCommand.LineBreak(2), PatchouliTextScanner.scan("$(br2)").get(0));
        assertEquals(new TextCommand.LineBreak(2), PatchouliTextScanner.scan("$(p)").get(0));
        assertEquals(new TextCommand.Reset(), PatchouliTextScanner.scan("$()").get(0));
        assertEquals(new TextCommand.Reset(), PatchouliTextScanner.scan("$(clear)").get(0));
        assertEquals(new TextCommand.ResetColor(), PatchouliTextScanner.scan("$(nocolor)").get(0));
    }

    @Test
    void keepsUnrecognisedCommandsVerbatimLikePatchouliDoes() {
        List<TextCommand> commands = PatchouliTextScanner.scan("a$(whatever)b");
        assertEquals(new TextCommand.Unknown("whatever"), commands.get(1));
        assertEquals("a$(whatever)b", AgeratumTextWriter.write(commands).markdown());
    }

    // -------------------------------------------------------------------- writing

    @Test
    void escapesMarkdownAndTagSignificantCharacters() {
        assertEquals("2 \\* 3 \\[x\\] a \\< b",
                AgeratumTextWriter.write(List.of(new TextCommand.Text("2 * 3 [x] a < b"))).markdown());
    }

    @Test
    void keepsMarkdownLinksInsideTheStyleTags() {
        List<TextCommand> commands = PatchouliTextScanner.scan("$(#ff0000)$(l:items/staff)Staff$(/l) tail$()");
        String markdown = AgeratumTextWriter.write(commands).markdown();
        // A Markdown link is not a tag, so the colour does not have to be closed around it; the
        // link simply sits inside the colour, and the tail stays coloured until $() resets.
        assertEquals("<color=#ff0000>[Staff](patchouli:items/staff) tail</color>", markdown);
    }

    @Test
    void resetClosesEveryOpenCluster() {
        // Books use $() to close a link instead of $(/l); the writer still has to emit the
        // closing bracket or the rest of the page becomes part of the link.
        List<TextCommand> commands = PatchouliTextScanner.scan("$(l:items/staff)Staff$() after");
        assertEquals("[Staff](patchouli:items/staff) after",
                AgeratumTextWriter.write(commands).markdown());
    }

    @Test
    void emitsNestedHoverAndClickTags() {
        List<TextCommand> commands = PatchouliTextScanner.scan("$(t:tip)$(c:/say hi)text$(/c)$(/t)");
        String markdown = AgeratumTextWriter.write(commands).markdown();
        assertEquals("<hover type=\"SHOW_TEXT\" data=\"tip\"><click type=\"RUN_COMMAND\" data=\"/say hi\">text</click></hover>",
                markdown);
    }

    @Test
    void rendersListsWithNestingAndBreaksBetweenParagraphs() {
        List<TextCommand> commands = PatchouliTextScanner.scan("intro$(li)one$(li2)two$(li)three$(br2)outro");
        assertEquals("intro\n\n- one\n    - two\n- three\n\noutro",
                AgeratumTextWriter.write(commands).markdown());
    }

    @Test
    void reportsWhatItHadToDrop() {
        AgeratumTextWriter.Result result =
                AgeratumTextWriter.write(PatchouliTextScanner.scan("$(n)styled$(playername)"));
        assertEquals(1, result.droppedUnderlines(), "Ageratum has no underline tag");
        assertEquals(1, result.droppedPlayerNames(), "a player name cannot be resolved offline");
    }

    // ----------------------------------------------------------------- corpus test

    /**
     * The acceptance criterion for this stage: every page of the real book scans and renders,
     * and every one of its internal links resolves.
     */
    @Test
    void convertsEveryHexModPageAndResolvesEveryLink() throws IOException {
        Assumptions.assumeTrue(Files.isRegularFile(HEXMOD_BOOK_JSON) && Files.isRegularFile(HEXMOD_EN_US),
                "HexMod checkout not present");

        Map<String, String> lang = Json5.flatten(Files.readString(HEXMOD_EN_US, StandardCharsets.UTF_8));
        MacroExpander expander = MacroExpander.of(readBookMacros());

        int pages = 0;
        int links = 0;
        int anchoredLinks = 0;
        int externalLinks = 0;
        int keybinds = 0;
        int droppedUnderlines = 0;
        List<String> unresolved = new ArrayList<>();
        List<String> unknown = new ArrayList<>();

        for (Map.Entry<String, String> entry : lang.entrySet()) {
            if (!entry.getKey().startsWith("hexcasting.page.")) {
                continue;
            }
            pages++;
            String expanded = expander.expand(entry.getValue()).orThrow();
            List<TextCommand> commands = PatchouliTextScanner.scan(expanded);

            for (TextCommand command : commands) {
                if (command instanceof TextCommand.LinkStart start) {
                    links++;
                    if (start.anchor() != null) {
                        anchoredLinks++;
                    }
                    if (start.external()) {
                        externalLinks++;
                    }
                    if (start.target().isBlank()) {
                        unresolved.add(entry.getKey());
                    }
                } else if (command instanceof TextCommand.Keybind) {
                    keybinds++;
                }
            }

            AgeratumTextWriter.Result result = AgeratumTextWriter.write(commands);
            droppedUnderlines += result.droppedUnderlines();
            unknown.addAll(result.unknownCommands());
        }

        assertTrue(pages > 500, "expected >500 pages, got " + pages);
        assertTrue(links > 350, "expected the book's ~370 links, got " + links);
        assertTrue(anchoredLinks > 100, "expected >100 anchored links, got " + anchoredLinks);
        assertTrue(externalLinks < 20, "most links are internal, got " + externalLinks);
        assertTrue(keybinds >= 5, "expected keybind insertions, got " + keybinds);
        assertEquals(4, droppedUnderlines, "$(n) appears four times in the book");
        assertTrue(unresolved.isEmpty(), "links with no target: " + unresolved);
        assertTrue(unknown.isEmpty(), "commands the converter does not understand: " + unknown);
    }

    @Test
    void convertsARealHexModPage() throws IOException {
        Assumptions.assumeTrue(Files.isRegularFile(HEXMOD_BOOK_JSON) && Files.isRegularFile(HEXMOD_EN_US),
                "HexMod checkout not present");

        Map<String, String> lang = Json5.flatten(Files.readString(HEXMOD_EN_US, StandardCharsets.UTF_8));
        MacroExpander expander = MacroExpander.of(readBookMacros());

        // A page whose source nests a macro, a link and the $() terminator.
        String raw = lang.get("hexcasting.page.patterns_as_iotas.further_notes.1");
        AgeratumTextWriter.Result result = AgeratumTextWriter.fromMacroExpanded(
                expander.expand(raw).orThrow(), AgeratumTextWriter.LinkResolver.passthrough());

        assertFalse(result.markdown().contains("$/"), "terminator survived");
        assertFalse(result.markdown().contains("_Hex"), "bare-word macro survived");
        assertTrue(result.markdown().contains("[") && result.markdown().contains("](patchouli:patterns/patterns_as_iotas#hexcasting:escape)"),
                "link not converted: " + result.markdown());
        assertTrue(result.unknownCommands().isEmpty(), result.unknownCommands().toString());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> readBookMacros() throws IOException {
        Map<String, Object> root =
                (Map<String, Object>) Json5.parse(Files.readString(HEXMOD_BOOK_JSON, StandardCharsets.UTF_8));
        Object macros = root.get("macros");
        assertTrue(macros instanceof Map<?, ?>, "book.json has no macros object");
        Map<String, String> result = new LinkedHashMap<>();
        ((Map<String, Object>) macros).forEach((key, value) -> result.put(key, String.valueOf(value)));
        return result;
    }
}
