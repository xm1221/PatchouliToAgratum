package cn.xm1221.pta.core.text;

/**
 * One parsed {@code $(...)} command, or a run of literal text between commands.
 *
 * <p>This mirrors Patchouli's own model rather than a tree. Patchouli renders text as a flat
 * list of spans plus a mutable style state, and its openers and closers are not required to
 * nest: {@code $()} resets everything at once, and books routinely use it to close a link or
 * a tooltip instead of {@code $(/l)} or {@code $(/t)}. Anything tree-shaped would have to fix
 * up unbalanced input; a flat event stream reproduces the renderer exactly.</p>
 *
 * <p>See {@code BookTextParser}'s static block for the source of every case below.</p>
 */
public sealed interface TextCommand {
    // ------------------------------------------------------------- literal content

    /** Literal text with no command semantics. */
    record Text(String value) implements TextCommand {
    }

    /**
     * {@code $(br)} is one break, {@code $(br2)} / {@code $(2br)} / {@code $(p)} are two.
     */
    record LineBreak(int count) implements TextCommand {
    }

    /**
     * {@code $(li)}, {@code $(li2)} … {@code $(li9)}. Patchouli indents by {@code depth * 4}
     * pixels and alternates its bullet by parity; Markdown lists carry the nesting instead.
     *
     * @param depth 1 for {@code $(li)}, otherwise the digit
     */
    record ListItem(int depth) implements TextCommand {
    }

    // ---------------------------------------------------------------------- styling

    /** {@code $(#rgb)} or {@code $(#rrggbb)}, normalised to {@code #rrggbb}. */
    record Color(String hex) implements TextCommand {
    }

    /** {@code $(0)} … {@code $(f)}, the legacy sixteen colour codes. */
    record ColorCode(char code) implements TextCommand {
    }

    /** {@code $(k)}/{@code $(obf)}, {@code $(l)}/{@code $(bold)}, and friends. */
    record Format(Kind kind) implements TextCommand {
        /** The formatting toggles Patchouli understands. */
        public enum Kind {
            /** {@code $(k)}, {@code $(obf)} */
            OBFUSCATED,
            /** {@code $(l)}, {@code $(bold)} */
            BOLD,
            /** {@code $(m)}, {@code $(strike)} */
            STRIKETHROUGH,
            /** {@code $(n)}, {@code $(underline)} */
            UNDERLINE,
            /** {@code $(o)}, {@code $(italic)}, {@code $(italics)} */
            ITALIC
        }
    }

    /** {@code $()}, {@code $(reset)}, {@code $(clear)}: drop every style and open cluster. */
    record Reset() implements TextCommand {
    }

    /** {@code $(nocolor)}: back to the book's base colour, keeping the other formats. */
    record ResetColor() implements TextCommand {
    }

    // ------------------------------------------------------------------- clusters

    /**
     * {@code $(l:target[#anchor])}. An {@code http}-ish target is an external URL, otherwise it
     * names an entry or category in the same book.
     */
    record LinkStart(String target, String anchor, boolean external) implements TextCommand {
    }

    /** {@code $(/l)}. */
    record LinkEnd() implements TextCommand {
    }

    /** {@code $(t:text)} / {@code $(tooltip:text)}. */
    record TooltipStart(String text) implements TextCommand {
    }

    /** {@code $(/t)}. */
    record TooltipEnd() implements TextCommand {
    }

    /** {@code $(c:/command)} / {@code $(command:/command)}. */
    record CommandStart(String command) implements TextCommand {
    }

    /** {@code $(/c)}. */
    record CommandEnd() implements TextCommand {
    }

    // ------------------------------------------------------------------ insertions

    /** {@code $(k:keybind)}. */
    record Keybind(String name) implements TextCommand {
    }

    /** {@code $(playername)}. Not resolvable offline. */
    record PlayerName() implements TextCommand {
    }

    /**
     * A command Patchouli does not know. Its renderer emits the text verbatim, so the converter
     * keeps it too and reports it.
     */
    record Unknown(String body) implements TextCommand {
    }
}
