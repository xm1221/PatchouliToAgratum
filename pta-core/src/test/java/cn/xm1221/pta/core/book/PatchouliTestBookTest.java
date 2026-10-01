package cn.xm1221.pta.core.book;

import cn.xm1221.pta.core.book.page.PageTypeRegistry;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The converter against a book it was not written for.
 *
 * <p>Hex Casting's book is the one this project was built around, so nothing about it proves the
 * converter generalises: its entries are ordinary, its page types are the well-behaved ones and its
 * language file is complete. Patchouli's own comprehensive test book is the opposite — it exists to
 * exercise every page type and component, including the ones nobody writes by hand — and running
 * the converter over it is the cheapest honest check that the next mod's book will not fall over.
 * The book ships in Patchouli's repository rather than its jar, so the test runs only when that
 * checkout is present.</p>
 */
class PatchouliTestBookTest {
    private static final Path RESOURCES = Path.of("E:/miemod/libs/Patchouli/Xplat/src/main/resources");
    private static final String BOOK = "comprehensive_test_book";

    private static BookConverter.Output convert() {
        BookLayout layout = BookLayout.load(
                BookSource.ofDirectory(RESOURCES, "patchouli", BOOK), "patchouli", BOOK, "en_us");
        return BookConverter.convert(layout, Map.of(), PageTypeRegistry.standard());
    }

    @Test
    void convertsEveryPageOfPatchouliOwnTestBook() {
        Assumptions.assumeTrue(Files.isDirectory(RESOURCES.resolve("assets/patchouli/patchouli_books/" + BOOK)),
                "Patchouli checkout not present");

        BookConverter.Output output = convert();

        // Every document has to be a document, whatever the page it came from.
        assertTrue(output.documents().size() > 10, output.documents().keySet().toString());
        for (Map.Entry<String, String> document : output.documents().entrySet()) {
            String text = document.getValue();
            assertTrue(text.startsWith("---\n"), document.getKey() + ": " + text);
            assertTrue(text.contains("title: \""), document.getKey() + ": " + text);
            assertFalse(text.isBlank(), document.getKey());
        }
    }

    /**
     * Nothing the book can express may be lost, and what is left to Patchouli has to be exactly the
     * page types with no Ageratum equivalent rather than a silent fallback for the whole book.
     *
     * <p>The exact map is pinned because every entry in it is a decision: the book's own custom
     * templates (a page type nothing can convert), the pages that need a 3D preview or a quest, the
     * three spotlights that name an item tag — Patchouli writes those as
     * {@code tag:minecraft:piglin_loved}, which is not an id — and the two entities that carry NBT.
     * Everything else in the book converts, which is what the map's absence says.</p>
     */
    @Test
    void hostsOnlyThePageTypesWithNoEquivalent() {
        Assumptions.assumeTrue(Files.isDirectory(RESOURCES.resolve("assets/patchouli/patchouli_books/" + BOOK)),
                "Patchouli checkout not present");

        Map<String, Integer> hosted = convert().report().hostedPageTypes();

        assertEquals(Map.of(
                "patchouli:builtin_components_1", 1,
                "patchouli:builtin_components_2", 1,
                "patchouli:custom_component", 1,
                "patchouli:derive_ingr_to_stack", 1,
                "patchouli:entity", 2,
                "patchouli:multiblock", 3,
                "patchouli:nesting", 1,
                "patchouli:quest", 2,
                "patchouli:relations", 1,
                "patchouli:spotlight", 3), hosted, hosted.toString());
    }
}
