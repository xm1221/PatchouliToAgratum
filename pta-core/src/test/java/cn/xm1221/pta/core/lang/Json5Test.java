package cn.xm1221.pta.core.lang;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Json5Test {
    /**
     * A real, deeply nested, hand-edited language file. Skips when the checkout is absent so
     * the suite still runs on a machine without it.
     */
    private static final Path HEXMOD_EN_US = Path.of(
            "E:/miemod/libs/HexMod/Common/src/main/resources/assets/hexcasting/lang/en_us.flatten.json5");

    @Test
    void parsesTheRealHexModLanguageFile() throws IOException {
        Assumptions.assumeTrue(Files.isRegularFile(HEXMOD_EN_US),
                "HexMod checkout not present at " + HEXMOD_EN_US);

        Map<String, String> flat = Json5.flatten(Files.readString(HEXMOD_EN_US, StandardCharsets.UTF_8));

        assertFalse(flat.isEmpty(), "no leaves parsed");
        assertTrue(flat.size() > 1000, "expected >1000 leaves, got " + flat.size());

        // A plain nested value.
        assertEquals("Hex Notebook", flat.get("item.hexcasting.book"));

        // A book page: this is the shape the converter actually consumes, including the
        // $(...) markup that later stages rewrite.
        String media = flat.get("hexcasting.page.media.1");
        assertNotNull(media, "hexcasting.page.media.1 is missing");
        assertTrue(media.contains("$(br2)"), "markup lost: " + media);
        assertTrue(media.startsWith("_Media is a form of mental energy"), media);

        // The one nested node in this file is the advancement names, and the locks in the book
        // read them to say what a page is waiting for. HexMod writes the namespace as its own
        // node, so the keys must land on it directly rather than gaining a second separator.
        assertEquals("Hexcasting Research", flat.get("advancement.hexcasting:root"));
        assertNotNull(flat.get("advancement.hexcasting:root.desc"),
                "advancement.hexcasting:root.desc is missing");
        assertEquals("Cardamom Steles #1", flat.get("advancement.hexcasting:lore/cardamom1"));
    }

    @Test
    void flattensNestedObjectsIntoDottedKeys() {
        Map<String, String> flat = Json5.flatten("""
                {
                  "a": { "b": { "c": "x", "n": 3 } },
                  "list": [ "one", "two" ]
                }
                """);
        assertEquals("x", flat.get("a.b.c"));
        assertEquals("3", flat.get("a.b.n"));
        assertEquals("one", flat.get("list.0"));
        assertEquals("two", flat.get("list.1"));
    }

    /**
     * A parent that already ends in a separator keeps it: {@code "advancement.hexcasting:"} plus
     * {@code root} is {@code advancement.hexcasting:root}, never {@code ...hexcasting:.root}, and
     * {@code "lore/"} plus {@code cardamom1} keeps its slash.
     */
    @Test
    void doesNotDoubleASeparatorAParentAlreadyEndsWith() {
        Map<String, String> flat = Json5.flatten("""
                {
                  "advancement.hexcasting:": {
                    "root": { "": "Hexcasting Research", "desc": "..." },
                    "lore/": { "cardamom1": { "": "Cardamom Steles #1" } }
                  },
                  "trailing.": { "leaf": "x" }
                }
                """);
        assertEquals("Hexcasting Research", flat.get("advancement.hexcasting:root"));
        assertEquals("...", flat.get("advancement.hexcasting:root.desc"));
        assertEquals("Cardamom Steles #1", flat.get("advancement.hexcasting:lore/cardamom1"));
        assertEquals("x", flat.get("trailing.leaf"));
    }

    /**
     * These files give a node both a value and children by using an empty key for the value,
     * which is how Minecraft's own flattening works. Missing this loses the node's own string
     * and leaves a trailing dot in the key.
     */
    @Test
    void treatsAnEmptyKeyAsTheParentKeyItself() {
        Map<String, String> flat = Json5.flatten("""
                {
                  "hexcasting": {
                    "category": {
                      "items": { "": "Items", "desc": "I devote this section..." },
                      "basics": { "": "Getting Started" }
                    }
                  }
                }
                """);
        assertEquals("Items", flat.get("hexcasting.category.items"));
        assertEquals("I devote this section...", flat.get("hexcasting.category.items.desc"));
        assertEquals("Getting Started", flat.get("hexcasting.category.basics"));
        assertFalse(flat.containsKey("hexcasting.category.items."), "trailing dot in a key");
    }

    @Test
    void acceptsCommentsBareKeysAndTrailingCommas() {
        Map<String, String> flat = Json5.flatten("""
                // leading comment
                {
                  book: "Hex Notebook",   // trailing comment
                  /* block
                     comment */
                  staff: {
                    oak: "Oak Staff",
                    cherry: "Cherry Staff",
                  },
                }
                """);
        assertEquals("Hex Notebook", flat.get("book"));
        assertEquals("Oak Staff", flat.get("staff.oak"));
        assertEquals("Cherry Staff", flat.get("staff.cherry"));
    }

    @Test
    void acceptsSingleQuotedStringsAndBackslashContinuations() {
        Map<String, String> flat = Json5.flatten("""
                {
                  'quoted': 'single',
                  continued: "first \\
                second",
                  between: \\
                "value"
                }
                """);
        assertEquals("single", flat.get("quoted"));
        assertEquals("first second", flat.get("continued"));
        assertEquals("value", flat.get("between"));
    }

    /**
     * The case that rules out ordinary JSON parsers: unescaped quotes inside a value. The
     * requirement is not that the value comes back byte-perfect — it cannot, the file is
     * ambiguous — but that the reader does not run off the end of the value and that every
     * following key still parses.
     */
    @Test
    void survivesUnescapedQuotesInsideValues() {
        Map<String, String> flat = Json5.flatten("""
                {
                  "a": "hexcasting":get_caster",
                  "b": "he said "hi" to me",
                  "c": "after"
                }
                """);
        assertEquals("he said \"hi\" to me", flat.get("b"));
        assertEquals("after", flat.get("c"), "reader did not resynchronise after an odd value");
        assertNotNull(flat.get("a"));
        assertTrue(flat.get("a").contains("get_caster"), flat.get("a"));
    }

    @Test
    void handlesEscapedQuotesAndUnicodeEscapes() {
        Map<String, String> flat = Json5.flatten("""
                {
                  "escaped": "say \\"hi\\"",
                  "unicode": "\\u00e9"
                }
                """);
        assertEquals("say \"hi\"", flat.get("escaped"));
        assertEquals("\u00e9", flat.get("unicode"));
    }

    @Test
    void parsesCrlfInput() {
        Map<String, String> flat = Json5.flatten("{\r\n  \"a\": \"one\",\r\n  \"b\": \"two\"\r\n}\r\n");
        assertEquals("one", flat.get("a"));
        assertEquals("two", flat.get("b"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "false", "null"})
    void parsesLiterals(String literal) {
        Object value = Json5.parse("{\"v\": " + literal + "}");
        assertTrue(value instanceof Map<?, ?>);
        assertTrue(((Map<?, ?>) value).containsKey("v"));
    }

    @Test
    void exposesNestedStructuresAsMapsAndLists() {
        Object root = Json5.parse("{\"outer\": {\"inner\": [1, 2]}}");
        assertTrue(root instanceof Map<?, ?>);
        Object outer = ((Map<?, ?>) root).get("outer");
        assertTrue(outer instanceof Map<?, ?>);
        Object inner = ((Map<?, ?>) outer).get("inner");
        assertTrue(inner instanceof List<?>, "expected a list, got " + inner);
        assertEquals(2, ((List<?>) inner).size());
    }

    @Test
    void returnsNullForEmptyInput() {
        assertEquals(null, Json5.parse("   \n  "));
    }

    @Test
    void rejectsInputThatDoesNotStartAnObject() {
        assertThrows(IllegalArgumentException.class, () -> Json5.parse("}"));
    }
}
