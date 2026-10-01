package cn.xm1221.pta.core.text;

import cn.xm1221.pta.core.lang.Json5;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MacroExpanderTest {
    private static final Path HEXMOD_BOOK_JSON = Path.of(
            "E:/miemod/libs/HexMod/Common/src/main/resources/data/hexcasting/patchouli_books/thehexbook/book.json");
    private static final Path HEXMOD_EN_US = Path.of(
            "E:/miemod/libs/HexMod/Common/src/main/resources/assets/hexcasting/lang/en_us.flatten.json5");

    @Test
    void appliesPatchouliBuiltInMacros() {
        MacroExpander expander = MacroExpander.of(null);
        assertEquals("$(#b0b)Staff", expander.expand("$(item)Staff").text());
        assertEquals("$(#490)casting", expander.expand("$(thing)casting").text());
        assertEquals("one$(br)two", expander.expand("one<br>two").text());
        assertEquals("done$()", expander.expand("done/$").text());
        // $(list has no closing parenthesis on purpose, so numbered variants work too.
        assertEquals("$(li)$(li2)$(li3)", expander.expand("$(list)$(list2)$(list3)").text());
    }

    @Test
    void bookMacrosOverrideBuiltIns() {
        MacroExpander expander = MacroExpander.of(Map.of("$(thing)", "$(#8d6acc)"));
        assertEquals("$(#8d6acc)casting", expander.expand("$(thing)casting").text());
        // The rest of the built-ins survive.
        assertEquals("$(#b0b)Staff", expander.expand("$(item)Staff").text());
    }

    @Test
    void expandsReplacementContentsAcrossPasses() {
        // _Media expands to something that still contains the /$ macro, so one pass is not
        // enough; this is exactly why Patchouli loops to a fixpoint.
        MacroExpander expander = MacroExpander.of(Map.of("_Media", "$(#74b3f2)Media/$"));
        MacroExpander.Expansion expansion = expander.expand("_Media is energy");
        assertTrue(expansion.converged());
        assertEquals("$(#74b3f2)Media$() is energy", expansion.text());
        assertTrue(expansion.iterations() >= 2, "expected at least two passes");
    }

    @Test
    void expandsChainedMacros() {
        Map<String, String> macros = new LinkedHashMap<>();
        macros.put("a", "b");
        macros.put("b", "c");
        assertEquals("c", MacroExpander.of(macros).expand("a").text());
    }

    @Test
    void stopsAtTheCapWhenReplacementsKeepGrowing() {
        // "a" -> "aa" never stabilises: every pass doubles the text.
        MacroExpander.Expansion expansion = MacroExpander.of(Map.of("a", "aa")).expand("a");
        assertFalse(expansion.converged(), "a growing macro must not report convergence");
        assertEquals(10, expansion.iterations());
        assertThrows(IllegalStateException.class, expansion::orThrow);
    }

    /**
     * A two-macro cycle is not a failure case: x -> y -> x lands back on the starting text
     * within a single pass, so the loop sees no change and stops. Worth pinning down, because
     * it is the opposite of what "circular macro" suggests.
     */
    @Test
    void aTwoMacroCycleReturnsToItsStartAndSoConverges() {
        Map<String, String> macros = new LinkedHashMap<>();
        macros.put("x", "y");
        macros.put("y", "x");
        MacroExpander.Expansion expansion = MacroExpander.of(macros).expand("x");
        assertTrue(expansion.converged());
        assertEquals("x", expansion.text());
    }

    /**
     * Hex Casting declares _Hexcasters before _Hexcaster. Applying the singular first would
     * turn the plural into "Hexcaster/$s", so declaration order has to be preserved.
     */
    @Test
    void appliesPrefixOverlappingMacrosInDeclarationOrder() {
        Map<String, String> macros = new LinkedHashMap<>();
        macros.put("_Hexcasters", "$(#b38ef3)Hexcasters/$");
        macros.put("_Hexcaster", "$(#b38ef3)Hexcaster/$");
        MacroExpander expander = MacroExpander.of(macros);
        assertEquals("$(#b38ef3)Hexcasters$() and $(#b38ef3)Hexcaster$()",
                expander.expand("_Hexcasters and _Hexcaster").text());
    }

    @Test
    void leavesUnknownCommandsAlone() {
        MacroExpander expander = MacroExpander.of(null);
        assertEquals("$(br2)$(p)$(l:items/staff)Staff$()",
                expander.expand("$(br2)$(p)$(l:items/staff)Staff$()").text());
    }

    @Test
    void treatsNullTextAsEmpty() {
        assertEquals("", MacroExpander.of(null).expand(null).text());
    }

    /**
     * The corpus test: every page of Hex Casting's book must reach a fixpoint.
     */
    @Test
    void expandsEveryHexModBookPageWithoutHittingTheCap() throws IOException {
        Assumptions.assumeTrue(Files.isRegularFile(HEXMOD_BOOK_JSON) && Files.isRegularFile(HEXMOD_EN_US),
                "HexMod checkout not present");

        Map<String, String> bookMacros = readBookMacros();
        assertEquals(11, bookMacros.size(), "Hex Casting declares 11 macros");

        Map<String, String> lang = Json5.flatten(Files.readString(HEXMOD_EN_US, StandardCharsets.UTF_8));
        MacroExpander expander = MacroExpander.of(bookMacros);

        int pages = 0;
        int rewritten = 0;
        for (Map.Entry<String, String> entry : lang.entrySet()) {
            if (!entry.getKey().startsWith("hexcasting.page.")) {
                continue;
            }
            pages++;
            String raw = entry.getValue();
            MacroExpander.Expansion expansion = expander.expand(raw);
            assertTrue(expansion.converged(),
                    () -> "macro cycle in " + entry.getKey() + ": " + raw);
            if (!expansion.text().equals(raw)) {
                rewritten++;
            }
        }

        assertTrue(pages > 500, "expected >500 pages, got " + pages);
        assertTrue(rewritten > 300, "expected most pages to be rewritten, got " + rewritten);
    }

    @Test
    void expandsARealHexModPage() throws IOException {
        Assumptions.assumeTrue(Files.isRegularFile(HEXMOD_BOOK_JSON) && Files.isRegularFile(HEXMOD_EN_US),
                "HexMod checkout not present");

        Map<String, String> lang = Json5.flatten(Files.readString(HEXMOD_EN_US, StandardCharsets.UTF_8));
        String raw = lang.get("hexcasting.page.media.1");
        assertTrue(raw.startsWith("_Media"), raw);

        String expanded = MacroExpander.of(readBookMacros()).expand(raw).orThrow();

        assertFalse(expanded.contains("_Media"), "bare-word macro survived: " + expanded);
        assertFalse(expanded.contains("/$"), "the /$ terminator survived: " + expanded);
        assertTrue(expanded.contains("$(#74b3f2)"), "media colour missing: " + expanded);
        // Commands that are not macros must pass through untouched.
        assertTrue(expanded.contains("$(br2)"), expanded);
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
