package cn.xm1221.pta.core.text;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Renders {@link TextCommand}s as Ageratum Markdown.
 *
 * <p>The hard part is that Patchouli's styling is a mutable state, not a tree: a colour stays
 * set until another colour or {@code $()} arrives, formats only ever turn on, and {@code $()}
 * closes a link just as happily as {@code $(/l)}. Emitting that directly produces unbalanced
 * tags. This writer therefore keeps the current style, closes and reopens it around any
 * cluster boundary, and always emits styles in one canonical order, so the output is always
 * well formed Markdown regardless of how the source was written.</p>
 *
 * <p>Canonical order, outermost first: colour, then obfuscated, bold, strikethrough, underline,
 * italic, with link, tooltip and command clusters wrapping the lot.</p>
 */
public final class AgeratumTextWriter {
    /** Minecraft's sixteen legacy colour codes, as Ageratum hex colours. */
    private static final String[] LEGACY_COLORS = {
            "#000000", "#0000aa", "#00aa00", "#00aaaa",
            "#aa0000", "#aa00aa", "#ffaa00", "#aaaaaa",
            "#555555", "#5555ff", "#55ff55", "#55ffff",
            "#ff5555", "#ff55ff", "#ffff55", "#ffffff",
    };

    private static final List<TextCommand.Format.Kind> FORMAT_ORDER = List.of(
            TextCommand.Format.Kind.OBFUSCATED,
            TextCommand.Format.Kind.BOLD,
            TextCommand.Format.Kind.STRIKETHROUGH,
            TextCommand.Format.Kind.UNDERLINE,
            TextCommand.Format.Kind.ITALIC
    );

    /** Resolves a Patchouli link target to the href to emit. */
    @FunctionalInterface
    public interface LinkResolver {
        /**
         * @param target   entry or category id, or a URL when {@code external}
         * @param anchor   anchor within the entry, may be {@code null}
         * @param external whether the target is an {@code http(s)} URL
         * @return the Markdown link destination
         */
        String resolve(String target, String anchor, boolean external);

        /** Leaves the Patchouli target in place, for tests and for callers that rewrite later. */
        static LinkResolver passthrough() {
            return (target, anchor, external) -> external
                    ? target
                    : "patchouli:" + target + (anchor == null ? "" : "#" + anchor);
        }
    }

    /**
     * The rendered text plus what had to be dropped.
     *
     * @param markdown           the Ageratum Markdown
     * @param droppedUnderlines  {@code $(n)} occurrences; Ageratum has no underline tag
     * @param droppedPlayerNames {@code $(playername)} occurrences; not resolvable offline
     * @param unknownCommands    bodies of commands Patchouli itself passes through verbatim
     */
    public record Result(String markdown, int droppedUnderlines, int droppedPlayerNames,
                         List<String> unknownCommands) {
    }

    private enum ClusterKind { LINK, TOOLTIP, COMMAND }

    private record Cluster(ClusterKind kind, String open, String close, boolean wrapsStyles) {
    }

    private final LinkResolver linkResolver;
    private final StringBuilder out = new StringBuilder();
    private final Deque<Cluster> clusters = new ArrayDeque<>();

    private String color;
    private final EnumSet<TextCommand.Format.Kind> formats =
            EnumSet.noneOf(TextCommand.Format.Kind.class);
    /** Whether the current style tags have actually been written out. */
    private boolean stylesOpen;
    /** Whether we are inside a list, so the next item starts a new line rather than a new list. */
    private boolean inList;
    private int droppedUnderlines;
    private int droppedPlayerNames;
    private final List<String> unknownCommands = new ArrayList<>();

    private AgeratumTextWriter(LinkResolver linkResolver) {
        this.linkResolver = linkResolver;
    }

    /**
     * Renders commands with the default link resolver.
     *
     * @param commands scanned commands, i.e. macro expansion has already happened
     */
    public static Result write(List<TextCommand> commands) {
        return write(commands, LinkResolver.passthrough());
    }

    /**
     * Renders commands.
     *
     * @param commands     scanned commands, i.e. macro expansion has already happened
     * @param linkResolver maps Patchouli link targets onto Ageratum document paths
     */
    public static Result write(List<TextCommand> commands, LinkResolver linkResolver) {
        AgeratumTextWriter writer = new AgeratumTextWriter(linkResolver);
        writer.run(commands);
        return new Result(writer.finish(), writer.droppedUnderlines, writer.droppedPlayerNames,
                List.copyOf(writer.unknownCommands));
    }

    /**
     * Convenience pipeline: expand macros, scan, render.
     *
     * @param macroExpanded text that has already been through {@link MacroExpander}
     */
    public static Result fromMacroExpanded(String macroExpanded, LinkResolver linkResolver) {
        return write(PatchouliTextScanner.scan(macroExpanded), linkResolver);
    }

    private void run(List<TextCommand> commands) {
        for (TextCommand command : commands) {
            switch (command) {
                case TextCommand.Text text -> appendEscaped(text.value());
                case TextCommand.LineBreak lineBreak -> {
                    this.out.append(lineBreak.count() >= 2 ? "\n\n" : "\n");
                    // A paragraph break is what ends a list; a single break stays inside it.
                    if (lineBreak.count() >= 2) {
                        this.inList = false;
                    }
                }
                case TextCommand.ListItem item -> appendListItem(item.depth());
                case TextCommand.Color value -> setColor(value.hex());
                case TextCommand.ColorCode code -> setColor(legacyColor(code.code()));
                case TextCommand.Format format -> addFormat(format.kind());
                case TextCommand.Reset ignored -> reset();
                case TextCommand.ResetColor ignored -> setColor(null);
                case TextCommand.LinkStart link -> pushCluster(
                        ClusterKind.LINK,
                        "[",
                        "](" + this.linkResolver.resolve(link.target(), link.anchor(), link.external()) + ")",
                        false);
                case TextCommand.LinkEnd ignored -> popCluster(ClusterKind.LINK);
                case TextCommand.TooltipStart tooltip -> pushCluster(
                        ClusterKind.TOOLTIP,
                        "<hover type=\"SHOW_TEXT\" data=\"" + escapeAttribute(tooltip.text()) + "\">",
                        "</hover>",
                        true);
                case TextCommand.TooltipEnd ignored -> popCluster(ClusterKind.TOOLTIP);
                case TextCommand.CommandStart command1 -> pushCluster(
                        ClusterKind.COMMAND,
                        "<click type=\"RUN_COMMAND\" data=\"" + escapeAttribute(command1.command()) + "\">",
                        "</click>",
                        true);
                case TextCommand.CommandEnd ignored -> popCluster(ClusterKind.COMMAND);
                case TextCommand.Keybind keybind -> this.out.append(
                        "<key id=\"" + escapeAttribute(keybind.name()) + "\"/>");
                case TextCommand.PlayerName ignored -> this.droppedPlayerNames++;
                case TextCommand.Unknown unknown -> {
                    this.unknownCommands.add(unknown.body());
                    appendEscaped("$(" + unknown.body() + ")");
                }
            }
            // Note: inList is deliberately not reset here. The text that follows $(li) belongs
            // to that item, so the list continues until an explicit paragraph break ends it.
        }

        closeAllClusters();
        closeStyles();
    }

    private void appendListItem(int depth) {
        this.out.append(this.inList ? "\n" : "\n\n");
        this.out.append("    ".repeat(Math.max(0, depth - 1))).append("- ");
        this.inList = true;
    }

    // ------------------------------------------------------------------ style state

    private void setColor(String hex) {
        if (Objects.equals(this.color, hex)) {
            return;
        }
        closeStyles();
        this.color = hex;
        openStyles();
    }

    private void addFormat(TextCommand.Format.Kind kind) {
        if (kind == TextCommand.Format.Kind.UNDERLINE) {
            // Ageratum has no underline tag; counted so the report can show the loss.
            this.droppedUnderlines++;
            return;
        }
        if (!this.formats.add(kind)) {
            return;
        }
        closeStyles();
        openStyles();
    }

    private void reset() {
        closeAllClusters();
        closeStyles();
        this.color = null;
        this.formats.clear();
    }

    private void closeStyles() {
        if (!this.stylesOpen) {
            return;
        }
        StringBuilder closing = new StringBuilder();
        for (int i = FORMAT_ORDER.size() - 1; i >= 0; i--) {
            TextCommand.Format.Kind kind = FORMAT_ORDER.get(i);
            if (this.formats.contains(kind)) {
                closing.append(closerFor(kind));
            }
        }
        if (this.color != null) {
            closing.append("</color>");
        }
        this.out.append(closing);
        this.stylesOpen = false;
    }

    private void openStyles() {
        if (this.stylesOpen || (this.color == null && this.formats.isEmpty())) {
            return;
        }
        if (this.color != null) {
            this.out.append("<color=").append(this.color).append('>');
        }
        for (TextCommand.Format.Kind kind : FORMAT_ORDER) {
            if (this.formats.contains(kind)) {
                this.out.append(openerFor(kind));
            }
        }
        this.stylesOpen = true;
    }

    private static String openerFor(TextCommand.Format.Kind kind) {
        return switch (kind) {
            case OBFUSCATED -> "<o>";
            case BOLD -> "**";
            case STRIKETHROUGH -> "~~";
            case ITALIC -> "*";
            case UNDERLINE -> "";
        };
    }

    private static String closerFor(TextCommand.Format.Kind kind) {
        return switch (kind) {
            case OBFUSCATED -> "</o>";
            case BOLD -> "**";
            case STRIKETHROUGH -> "~~";
            case ITALIC -> "*";
            case UNDERLINE -> "";
        };
    }

    // ---------------------------------------------------------------- cluster state

    private void pushCluster(ClusterKind kind, String open, String close, boolean wrapsStyles) {
        if (wrapsStyles) {
            // A real tag has to stay outside any style tags, or closing the style later would
            // cross it. Markdown links are not tags, so they can simply sit inside the styles.
            closeStyles();
        }
        this.clusters.push(new Cluster(kind, open, close, wrapsStyles));
        this.out.append(open);
        if (wrapsStyles) {
            openStyles();
        }
    }

    private void popCluster(ClusterKind kind) {
        if (!hasCluster(kind)) {
            return;
        }
        boolean wrapsStyles = topClusterWrapsStyles(kind);
        if (wrapsStyles) {
            closeStyles();
        }
        while (!this.clusters.isEmpty()) {
            Cluster cluster = this.clusters.pop();
            this.out.append(cluster.close());
            if (cluster.kind() == kind) {
                break;
            }
        }
        if (wrapsStyles) {
            openStyles();
        }
    }

    private boolean topClusterWrapsStyles(ClusterKind kind) {
        for (Cluster cluster : this.clusters) {
            if (cluster.kind() == kind) {
                return cluster.wrapsStyles();
            }
        }
        return false;
    }

    private void closeAllClusters() {
        closeStyles();
        while (!this.clusters.isEmpty()) {
            this.out.append(this.clusters.pop().close());
        }
    }

    private boolean hasCluster(ClusterKind kind) {
        return this.clusters.stream().anyMatch(cluster -> cluster.kind() == kind);
    }

    // ---------------------------------------------------------------------- escaping

    /**
     * Escapes the characters that would otherwise start Markdown syntax or an Ageratum tag.
     * {@code <} matters as much as the Markdown punctuation: Ageratum treats an angle-bracketed
     * word as a component tag.
     */
    private void appendEscaped(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' || c == '*' || c == '_' || c == '`' || c == '~' || c == '[' || c == ']' || c == '<') {
                this.out.append('\\');
            }
            this.out.append(c);
        }
    }

    private static String escapeAttribute(String text) {
        return text.replace("\\", "\\\\").replace("\"", "&quot;");
    }

    private static String legacyColor(char code) {
        int index = Character.digit(Character.toLowerCase(code), 16);
        return index < 0 || index >= LEGACY_COLORS.length
                ? LEGACY_COLORS[15]
                : LEGACY_COLORS[index].toLowerCase(Locale.ROOT);
    }

    private String finish() {
        // Collapse the runs of blank lines that adjacent breaks and list boundaries produce.
        String text = this.out.toString().replaceAll("\n{3,}", "\n\n");
        int start = 0;
        int end = text.length();
        while (start < end && Character.isWhitespace(text.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(start, end);
    }
}
