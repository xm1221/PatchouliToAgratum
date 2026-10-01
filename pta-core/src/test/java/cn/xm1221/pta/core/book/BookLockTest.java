package cn.xm1221.pta.core.book;

import cn.xm1221.pta.core.lang.Json5;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locking, in both directions: entries wait for their own advancement, and a chapter waits for
 * whichever of its entries unlocks first.
 *
 * <p>A lock is written into the document and resolved by the client, because whether an advancement
 * is earned is per player and changes while the game runs. What these tests can pin down is the
 * requirement each document carries, and that a document whose book declares no lock carries
 * none.</p>
 */
class BookLockTest {
    private static final Path HEXMOD_RESOURCES = Path.of("E:/miemod/libs/HexMod/Common/src/main/resources");
    private static final Path HEXMOD_LANG = HEXMOD_RESOURCES.resolve(
            "assets/hexcasting/lang/en_us.flatten.json5");

    /** The gate's opening tag, as the client component reads it. */
    private static final String GATE = "<pta:locked ";

    /** A book with one chapter whose entries are all locked, one with a single unlocked entry, a
     * nested chapter, and an empty one. */
    private static BookConverter.Output convertDemo() {
        return convertDemo(Map.of());
    }

    private static BookConverter.Output convertDemo(Map<String, String> lang) {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("data/demo/patchouli_books/demo/book.json", "{ name: \"Demo\" }");
        files.put("assets/demo/patchouli_books/demo/en_us/categories/locked.json",
                "{ name: \"Locked\", description: \"behind an advancement\" }");
        files.put("assets/demo/patchouli_books/demo/en_us/categories/open.json",
                "{ name: \"Open\", description: \"partly open\" }");
        files.put("assets/demo/patchouli_books/demo/en_us/categories/nested.json",
                "{ name: \"Nested\", description: \"has a child chapter\" }");
        files.put("assets/demo/patchouli_books/demo/en_us/categories/nested/child.json",
                "{ name: \"Child\", parent: \"demo:nested\" }");
        files.put("assets/demo/patchouli_books/demo/en_us/categories/empty.json",
                "{ name: \"Empty\", description: \"holds nothing\" }");

        files.put("assets/demo/patchouli_books/demo/en_us/entries/locked/a.json",
                "{ name: \"A\", category: \"demo:locked\", advancement: \"minecraft:story/root\","
                        + " pages: [{ type: \"patchouli:text\", text: \"Page one\" }] }");
        files.put("assets/demo/patchouli_books/demo/en_us/entries/locked/b.json",
                "{ name: \"B\", category: \"demo:locked\", secret: true,"
                        + " advancement: \"minecraft:story/mine_stone\", pages: [] }");
        files.put("assets/demo/patchouli_books/demo/en_us/entries/open/c.json",
                "{ name: \"C\", category: \"demo:open\", pages: [] }");
        files.put("assets/demo/patchouli_books/demo/en_us/entries/open/d.json",
                "{ name: \"D\", category: \"demo:open\", advancement: \"minecraft:story/root\","
                        + " pages: [] }");
        files.put("assets/demo/patchouli_books/demo/en_us/entries/nested/f.json",
                "{ name: \"F\", category: \"demo:nested\", advancement: \"minecraft:story/root\","
                        + " pages: [] }");
        files.put("assets/demo/patchouli_books/demo/en_us/entries/nested/child/e.json",
                "{ name: \"E\", category: \"demo:nested/child\","
                        + " advancement: \"minecraft:story/root\", pages: [] }");

        BookLayout layout = BookLayout.load(BookSource.of(files), "demo", "demo", "en_us");
        return BookConverter.convert(layout, lang);
    }

    /** The value of one attribute of the document's gate. */
    private static String attribute(String document, String name) {
        Matcher matcher = Pattern.compile(GATE + "[^>]*\\b" + name + "=\"([^\"]*)\"").matcher(document);
        assertTrue(matcher.find(), name + " is missing from " + document);
        return matcher.group(1);
    }

    /** The {@code pta_lock} line the front matter carries, or {@code null} when it carries none. */
    private static String declaredLock(String document) {
        Matcher matcher = Pattern.compile("^pta_lock: \"(.*)\"$", Pattern.MULTILINE).matcher(document);
        return matcher.find() ? matcher.group(1) : null;
    }

    @Test
    void locksAnEntryBehindItsOwnAdvancement() {
        String document = convertDemo().documents().get("locked/a");

        assertEquals("minecraft:story/root", attribute(document, "advancements"));
        assertTrue(document.endsWith("</pta:locked>\n"), document);
        // The title is inside the gate: a reader who has not unlocked it sees the notice, not the
        // entry's heading and prose.
        assertTrue(document.indexOf(GATE) < document.indexOf("# A"), document);
        assertTrue(document.indexOf("# A") < document.indexOf("Page one"), document);
        // The front matter stays outside the gate, so the sidebar still has a title to show.
        assertTrue(document.startsWith("---\n"), document);
        assertTrue(document.indexOf("---\n\n") < document.indexOf(GATE), document);
    }

    /**
     * The requirement is written as the achievement's name — for the reader of the file, and for the
     * notice, which says it when the client cannot name an advancement it has never seen.
     */
    @Test
    void namesTheRequirementTheWayTheAdvancementScreenDoes() {
        Map<String, String> documents = convertDemo(Map.of(
                "advancements.minecraft.story.root.title", "Stone Age",
                "advancement.minecraft:story/mine_stone", "Stone Age")).documents();

        String entry = documents.get("locked/a");
        assertEquals("Stone Age", declaredLock(entry));
        assertEquals("Stone Age", attribute(entry, "names"));
        // The id is what the client checks, so it stays.
        assertEquals("minecraft:story/root", attribute(entry, "advancements"));

        // A chapter names every requirement it waits for, in the order it lists them.
        assertEquals("Stone Age|Stone Age",
                attribute(documents.get("locked/index"), "names"));
    }

    /** Without a name to use, the id is still the exact truth about what is waited for. */
    @Test
    void fallsBackToTheIdWhenTheBookNeverNamesAnAdvancement() {
        String document = convertDemo().documents().get("locked/a");

        assertEquals("minecraft:story/root", declaredLock(document));
        assertEquals("minecraft:story/root", attribute(document, "names"));
    }

    @Test
    void leavesAnEntryWithoutAnAdvancementAlone() {
        String document = convertDemo().documents().get("open/c");

        assertFalse(document.contains(GATE), document);
        assertFalse(document.contains("pta_lock"), document);
    }

    @Test
    void keepsASecretEntrysRequirementHidden() {
        String document = convertDemo().documents().get("locked/b");

        assertTrue(document.contains("secret=\"true\""), document);
    }

    /** Patchouli locks a chapter while every entry in it is locked, and unlocks it on the first one. */
    @Test
    void locksAChapterUntilAnyOfItsEntriesUnlocks() {
        Map<String, String> documents = convertDemo().documents();

        String locked = documents.get("locked/index");
        assertEquals("minecraft:story/root,minecraft:story/mine_stone",
                attribute(locked, "advancements"));
        assertEquals("any", attribute(locked, "unlock"));
        // The chapter's list of entries is inside the gate, so a locked chapter does not reveal
        // which entries it holds — which is what Patchouli hides for a locked chapter.
        assertTrue(locked.indexOf(GATE) < locked.indexOf("](a)"), locked);
        assertTrue(locked.indexOf("# Locked") > locked.indexOf(GATE), locked);

        // One entry of this chapter has no advancement, so the chapter itself is never locked.
        assertFalse(documents.get("open/index").contains(GATE), documents.get("open/index"));
    }

    /** A chapter waits for its sub-chapters too, and repeats an advancement only once. */
    @Test
    void locksAChapterThroughItsSubChapters() {
        Map<String, String> documents = convertDemo().documents();

        String parent = documents.get("nested/index");
        assertEquals("minecraft:story/root", attribute(parent, "advancements"));
        assertEquals("any", attribute(parent, "unlock"));
        // A nested chapter's index is folded into its parent's directory, which is the only level
        // Ageratum's sidebar reaches.
        assertTrue(documents.get("nested/child__index").contains(GATE),
                documents.get("nested/child__index"));
    }

    /** Patchouli counts an empty chapter as unlocked, so it cannot lock its parent either. */
    @Test
    void neverLocksAnEmptyChapter() {
        String empty = convertDemo().documents().get("empty/index");

        assertFalse(empty.contains(GATE), empty);
    }

    /**
     * The real book: every entry that declares a lock carries it, and nothing else is gated beyond
     * the chapters those locks close.
     */
    @Test
    void locksTheRealBookTheWayTheBookDeclares() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(HEXMOD_RESOURCES)
                && Files.isRegularFile(HEXMOD_LANG), "HexMod checkout not present");
        BookLayout layout = BookLayout.load(
                BookSource.ofDirectory(HEXMOD_RESOURCES, "hexcasting", "thehexbook"),
                "hexcasting", "thehexbook", "en_us");
        Map<String, String> lang = Json5.flatten(Files.readString(HEXMOD_LANG, StandardCharsets.UTF_8));
        Map<String, String> documents = BookConverter.convert(layout, lang).documents();

        int gatedEntries = 0;
        int gatedChapters = 0;
        for (Map.Entry<String, String> document : documents.entrySet()) {
            String path = document.getKey();
            String text = document.getValue();
            boolean gated = text.contains(GATE);
            boolean declared = declaredLock(text) != null;
            boolean chapterIndex = path.endsWith("index") && !path.equals(BookConverter.ROOT_DOCUMENT);

            if (!gated) {
                assertFalse(declared, path + " declares a lock it does not carry");
                continue;
            }
            assertTrue(text.endsWith("</pta:locked>\n"), path + " leaves its lock open");
            if (chapterIndex) {
                gatedChapters++;
                assertTrue(text.contains("unlock=\"any\""), path + " locks a chapter on one entry");
                assertFalse(attribute(text, "names").contains(":"),
                        path + " names its requirements by id: " + attribute(text, "names"));
            } else {
                gatedEntries++;
                assertTrue(declared, path + " is locked without a declared advancement");
                // The book names every advancement it locks on, so a reader is told what a page
                // waits for by name rather than by id.
                assertFalse(declaredLock(text).contains(":"),
                        path + " declares its lock as an id: " + declaredLock(text));
            }
        }

        // Hex Casting's book is locked entry by entry, so this is not a vacuous check.
        assertTrue(gatedEntries > 50, "expected most entries to be locked, got " + gatedEntries);
        assertTrue(gatedChapters > 0, "expected at least one locked chapter");
    }
}
