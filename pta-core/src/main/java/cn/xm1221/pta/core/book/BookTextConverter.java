package cn.xm1221.pta.core.book;

import cn.xm1221.pta.core.report.ConversionReport;
import cn.xm1221.pta.core.text.AgeratumTextWriter;
import cn.xm1221.pta.core.text.MacroExpander;
import cn.xm1221.pta.core.text.PatchouliTextScanner;

/**
 * Converts one run of Patchouli book text into Ageratum Markdown.
 *
 * <p>Holds the three things every conversion needs — the book, its macros and the report — so that
 * callers only have to say where the text is going and what it says. Page renderers get one of
 * these through their context.</p>
 */
public final class BookTextConverter {
    private final BookLayout layout;
    private final MacroExpander macros;
    private final ConversionReport report;

    public BookTextConverter(BookLayout layout, ConversionReport report) {
        this.layout = layout;
        this.report = report;
        this.macros = MacroExpander.of(layout.macros());
    }

    public BookLayout layout() {
        return this.layout;
    }

    public ConversionReport report() {
        return this.report;
    }

    public MacroExpander macros() {
        return this.macros;
    }

    /**
     * Expands macros, scans commands and writes Markdown, rewriting internal links so they resolve
     * from the document the text ends up in.
     *
     * @param fromDocumentPath the document this text becomes part of
     * @param bookText         raw book text, already resolved through i18n
     * @return Markdown, empty when there is nothing to render
     */
    public String convert(String fromDocumentPath, String bookText) {
        if (bookText == null || bookText.isBlank()) {
            return "";
        }
        AgeratumTextWriter.Result result = AgeratumTextWriter.write(
                PatchouliTextScanner.scan(this.macros.expand(bookText).orThrow()),
                (target, anchor, external) -> {
                    if (external) {
                        return target;
                    }
                    String resolved = this.layout.resolveTarget(target);
                    if (resolved == null) {
                        // Not a document in this book, so leave the target alone and let it fail
                        // visibly rather than pointing somewhere arbitrary.
                        return target;
                    }
                    if (anchor != null) {
                        this.report.anchor(target);
                    }
                    return BookLayout.relativize(fromDocumentPath, resolved);
                });

        for (int i = 0; i < result.droppedUnderlines(); i++) {
            this.report.underline();
        }
        for (int i = 0; i < result.droppedPlayerNames(); i++) {
            this.report.playerName();
        }
        result.unknownCommands().forEach(this.report::unknownCommand);
        return result.markdown();
    }
}
