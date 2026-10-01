package cn.xm1221.pta.core.report;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversionReportTest {
    @Test
    void startsClean() {
        ConversionReport report = ConversionReport.empty();
        assertTrue(report.isClean());
        assertEquals("""
                # Conversion report

                - documents written: 0
                - dropped underlines: 0
                - dropped player names: 0

                Nothing was lost.
                """, report.toMarkdown());
    }

    @Test
    void countsRepeatedFindings() {
        ConversionReport report = ConversionReport.empty();
        report.anchor("hexcasting:get_caster");
        report.anchor("hexcasting:get_caster");
        report.anchor("hexcasting:escape");
        report.unknownCommand("wat");

        assertFalse(report.isClean());
        assertEquals(2, report.droppedAnchors().get("hexcasting:get_caster"));
        assertEquals(1, report.droppedAnchors().get("hexcasting:escape"));
        assertEquals(1, report.unknownCommands().get("wat"));
    }

    @Test
    void listsFindingsSortedForStableDiffs() {
        ConversionReport report = ConversionReport.empty();
        report.anchor("z");
        report.anchor("a");
        report.anchor("m");

        assertEquals("[a, m, z]", report.droppedAnchors().keySet().toString(),
                "a report that reorders between runs is useless for diffing");
    }

    @Test
    void rendersFindingsAsMarkdown() {
        ConversionReport report = ConversionReport.empty();
        report.document();
        report.document();
        report.underline();
        report.playerName();
        report.hostedPageType("patchouli:multiblock");
        report.unrenderedText("hexcasting.page.missing");

        String markdown = report.toMarkdown();
        assertTrue(markdown.contains("documents written: 2"), markdown);
        assertTrue(markdown.contains("dropped underlines: 1"), markdown);
        assertTrue(markdown.contains("dropped player names: 1"), markdown);
        assertTrue(markdown.contains("## Page types rendered by Patchouli"), markdown);
        assertTrue(markdown.contains("`patchouli:multiblock` x1"), markdown);
        assertTrue(markdown.contains("## Unresolved text keys"), markdown);
        assertFalse(markdown.contains("Nothing was lost"), markdown);
    }

    @Test
    void omitsSectionsWithNoFindings() {
        ConversionReport report = ConversionReport.empty();
        report.document();
        String markdown = report.toMarkdown();
        assertFalse(markdown.contains("## "), markdown);
    }

    @Test
    void doesNotLetCallersMutateTheCounts() {
        ConversionReport report = ConversionReport.empty();
        report.anchor("x");
        try {
            report.droppedAnchors().put("y", 1);
            throw new AssertionError("expected the map to be immutable");
        } catch (UnsupportedOperationException expected) {
            // The snapshot handed out must not be a live view of the accumulator.
        }
    }
}
