package cn.xm1221.pta.core.report;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Accumulates everything the conversion could not carry across faithfully.
 *
 * <p>A converter that silently loses content is worse than one that refuses, so every stage
 * records what it dropped and the whole run is summarised afterwards. Nothing here is an error:
 * a page type this stage does not render yet, an underline (Ageratum has no underline tag) and
 * a Patchouli anchor that could not be preserved are all expected, and all worth telling the
 * user about.</p>
 */
public final class ConversionReport {
    private final Map<String, Integer> droppedAnchors = new TreeMap<>();
    private final Map<String, Integer> unknownCommands = new TreeMap<>();
    private final Map<String, Integer> unsupportedPageTypes = new TreeMap<>();
    private final Map<String, Integer> unrenderedTextFields = new TreeMap<>();

    private int documents;
    private int droppedUnderlines;
    private int droppedPlayerNames;

    /** One entry document was produced. */
    public void document() {
        this.documents++;
    }

    /** {@code $(n)} was seen; Ageratum has no underline tag. */
    public void underline() {
        this.droppedUnderlines++;
    }

    /** {@code $(playername)} was seen; it cannot be resolved without a running client. */
    public void playerName() {
        this.droppedPlayerNames++;
    }

    /**
     * A {@code $(l:target#anchor)} link had to fall back to the document, losing its position.
     *
     * @param target the link target whose anchor was dropped
     */
    public void anchor(String target) {
        bump(this.droppedAnchors, target);
    }

    /** A command Patchouli itself passes through untouched. */
    public void unknownCommand(String body) {
        bump(this.unknownCommands, body);
    }

    /** A Patchouli page type with no renderer yet. */
    public void unsupportedPageType(String type) {
        bump(this.unsupportedPageTypes, type);
    }

    /**
     * A text field that was expected to hold a language key and did not resolve.
     *
     * @param key the missing key
     */
    public void unrenderedText(String key) {
        bump(this.unrenderedTextFields, key);
    }

    public int documents() {
        return this.documents;
    }

    public Map<String, Integer> droppedAnchors() {
        return frozen(this.droppedAnchors);
    }

    public Map<String, Integer> unknownCommands() {
        return frozen(this.unknownCommands);
    }

    public Map<String, Integer> unsupportedPageTypes() {
        return frozen(this.unsupportedPageTypes);
    }

    public Map<String, Integer> unrenderedTextFields() {
        return frozen(this.unrenderedTextFields);
    }

    /**
     * {@code Map.copyOf} does not preserve iteration order, which would make the report reorder
     * itself between runs and defeat diffing it.
     */
    private static Map<String, Integer> frozen(Map<String, Integer> source) {
        return Collections.unmodifiableMap(new TreeMap<>(source));
    }

    public int droppedUnderlines() {
        return this.droppedUnderlines;
    }

    public int droppedPlayerNames() {
        return this.droppedPlayerNames;
    }

    /**
     * @return {@code true} when nothing was lost and the conversion is lossless
     */
    public boolean isClean() {
        return this.droppedAnchors.isEmpty()
                && this.unknownCommands.isEmpty()
                && this.unsupportedPageTypes.isEmpty()
                && this.unrenderedTextFields.isEmpty()
                && this.droppedUnderlines == 0
                && this.droppedPlayerNames == 0;
    }

    /**
     * Renders a human-readable summary, suitable for writing next to the generated documents.
     */
    public String toMarkdown() {
        StringBuilder out = new StringBuilder();
        out.append("# Conversion report\n\n");
        out.append("- documents written: ").append(this.documents).append('\n');
        out.append("- dropped underlines: ").append(this.droppedUnderlines).append('\n');
        out.append("- dropped player names: ").append(this.droppedPlayerNames).append('\n');

        appendCounts(out, "Dropped anchors", this.droppedAnchors);
        appendCounts(out, "Unsupported page types", this.unsupportedPageTypes);
        appendCounts(out, "Unknown commands", this.unknownCommands);
        appendCounts(out, "Unresolved text keys", this.unrenderedTextFields);

        if (isClean()) {
            out.append("\nNothing was lost.\n");
        }
        return out.toString();
    }

    private static void appendCounts(StringBuilder out, String title, Map<String, Integer> counts) {
        if (counts.isEmpty()) {
            return;
        }
        out.append('\n').append("## ").append(title).append('\n');
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            out.append("- `").append(entry.getKey()).append("` x").append(entry.getValue()).append('\n');
        }
    }

    private static void bump(Map<String, Integer> counts, String key) {
        counts.merge(key == null ? "(null)" : key, 1, Integer::sum);
    }

    /** A fresh report with no findings, for tests. */
    public static ConversionReport empty() {
        return new ConversionReport();
    }

    /** Preserves insertion order, for callers that want a stable dump of raw findings. */
    public Map<String, Integer> snapshot() {
        Map<String, Integer> snapshot = new LinkedHashMap<>();
        snapshot.putAll(this.droppedAnchors);
        snapshot.putAll(this.unsupportedPageTypes);
        snapshot.putAll(this.unknownCommands);
        snapshot.putAll(this.unrenderedTextFields);
        return snapshot;
    }
}
