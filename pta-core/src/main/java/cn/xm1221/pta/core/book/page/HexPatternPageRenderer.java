package cn.xm1221.pta.core.book.page;

import java.util.List;
import java.util.Map;

/**
 * Renders Hex Casting's pattern pages: the op's name, its signature and its prose as Ageratum
 * text, with only the hexagon itself left to Hex Casting.
 *
 * <p>Hex Casting draws these pages through a template whose every text end is a page field or a
 * language key — the pattern's name, its {@code input}/{@code output} signature and its
 * description — so all of that survives the conversion untouched and only the vector pattern
 * needs the mod that owns it. The result is a page laid out as the original: heading on top,
 * pattern on the left with its signature underneath, prose on the right.</p>
 *
 * <p>Named after Hex Casting on purpose and safe without it: the page types are matched by id, and
 * a book with no such page is unaffected. Nothing in this module knows the mod exists.</p>
 *
 * @see #TYPES
 */
public final class HexPatternPageRenderer implements PageRenderer {
    /**
     * The page types this renderer answers for.
     *
     * <p>{@code manual_pattern} and {@code manual_pattern_nosig} take their pattern from the page
     * rather than from an action, and are used by addons; {@code nosig} is the variant without a
     * stroke-order hint.</p>
     */
    public static final List<String> TYPES = List.of(
            "hexcasting:pattern",
            "hexcasting:manual_pattern",
            "hexcasting:manual_pattern_nosig");

    /** Where every action's name lives, in both of its spellings. */
    private static final String ACTION_PREFIX = "hexcasting.action.";

    /** The infix that turns an action name into the book's name for it. */
    private static final String BOOK_INFIX = "book.";

    /** What Patchouli's own pattern template draws between the two halves of a signature. */
    private static final String SIGNATURE_ARROW = " → ";

    /** Hex Casting's default starting direction, for a manual pattern that names none. */
    private static final String DEFAULT_DIRECTION = "EAST";

    @Override
    public String render(PageRenderContext context) {
        String pattern = pattern(context);
        if (pattern == null) {
            // No action and no pattern string: nothing here knows what to draw, so let the host
            // fall back to Patchouli rather than emit a page that has lost its only picture.
            return null;
        }

        StringBuilder markdown = new StringBuilder();
        String title = title(context);
        if (!title.isBlank()) {
            markdown.append("## ").append(title).append("\n\n");
        }

        String body = context.renderText(context.text());
        if (body.isBlank()) {
            // Nothing to sit beside the pattern, so no row either: a row with an empty column
            // would just indent the pattern for no reason.
            return markdown.append(pattern).toString();
        }
        return markdown.append("<row>\n\n")
                .append(pattern)
                .append("\n\n---\n\n")
                .append(body)
                .append("\n\n</row>")
                .toString();
    }

    /**
     * The pattern's name, as Hex Casting's template resolves it.
     *
     * <p>An explicit {@code header} wins. Otherwise the name is an action lookup, and Hex Casting
     * checks whether the plain action name exists before deciding between
     * {@code hexcasting.action.book.<op>} and {@code hexcasting.action.<op>} — most actions have a
     * book name ("Stadiometer's Prfn.") that differs from their casting name.</p>
     */
    private static String title(PageRenderContext context) {
        String header = context.raw("header");
        if (header != null && !header.isBlank()) {
            return context.localise(header.trim());
        }

        String opId = context.raw("op_id");
        if (opId == null || opId.isBlank()) {
            return context.title();
        }

        String action = ACTION_PREFIX + opId.trim();
        String bookName = ACTION_PREFIX + BOOK_INFIX + opId.trim();
        return context.hasLocalisation(bookName)
                ? context.localise(bookName)
                : context.localise(action);
    }

    /**
     * The component that draws the hexagon, carrying everything the renderer can resolve offline.
     *
     * @return the tag, or {@code null} when the page names neither an action nor a pattern
     */
    private static String pattern(PageRenderContext context) {
        String opId = context.raw("op_id");
        if (opId != null && !opId.isBlank()) {
            return tag("op", opId.trim(), context);
        }
        String patterns = manualPatterns(context);
        if (patterns != null) {
            return tag("patterns", patterns, context);
        }
        return null;
    }

    /**
     * A manual page's patterns, in the compact form this converter writes them as.
     *
     * <p>The book stores them as {@code {startdir, signature, q, r}} objects and lets Patchouli
     * hand the JSON back to Hex Casting as text; that round trip is not worth reproducing in an
     * attribute, so the four fields become {@code DIR:SIGNATURE[@q,r]} and several patterns are
     * separated by {@code ;}. Nothing in the form is a quote or an angle bracket, which is what
     * keeps it safe to put in a Markdown tag.</p>
     *
     * @return the compact form, or {@code null} when the page has no usable pattern
     */
    private static String manualPatterns(PageRenderContext context) {
        Object value = context.value("patterns");
        if (value instanceof String text) {
            // Some addons write the pattern out as a plain angle signature.
            return text.isBlank() ? null : text.trim();
        }

        List<?> entries;
        if (value instanceof List<?> list) {
            entries = list;
        } else if (value instanceof Map<?, ?> single) {
            entries = List.of(single);
        } else {
            return null;
        }

        StringBuilder patterns = new StringBuilder();
        for (Object entry : entries) {
            if (!(entry instanceof Map<?, ?> pattern)) {
                continue;
            }
            String signature = string(pattern.get("signature"));
            if (signature == null) {
                continue;
            }
            String direction = string(pattern.get("startdir"));
            if (!patterns.isEmpty()) {
                patterns.append(';');
            }
            patterns.append(direction == null ? DEFAULT_DIRECTION : direction)
                    .append(':').append(signature);

            int q = number(pattern.get("q"));
            int r = number(pattern.get("r"));
            if (q != 0 || r != 0) {
                patterns.append('@').append(q).append(',').append(r);
            }
        }
        return patterns.isEmpty() ? null : patterns.toString();
    }

    /**
     * @param value a JSON value that should be a string
     * @return the trimmed text, or {@code null} when it is not a usable string
     */
    private static String string(Object value) {
        return value instanceof String text && !text.isBlank() ? text.trim() : null;
    }

    /**
     * @param value a JSON value that should be a number
     * @return the number, or {@code 0} when it is absent or not numeric
     */
    private static int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static String tag(String name, String value, PageRenderContext context) {
        StringBuilder tag = new StringBuilder("<pta:pattern");
        attribute(tag, name, value);
        attribute(tag, "stroke_order", strokeOrder(context));
        attribute(tag, "io", signature(context));
        return tag.append("/>").toString();
    }

    /**
     * Whether Hex Casting should hint the stroke order.
     *
     * <p>A manual page may say so itself; otherwise the page type decides — that is the only
     * difference between {@code manual_pattern} and {@code manual_pattern_nosig}. Action lookups
     * need nothing here: Hex Casting derives the hint from the action's own tags.</p>
     *
     * @return the value to write, or {@code null} to leave the choice to Hex Casting
     */
    private static String strokeOrder(PageRenderContext context) {
        String declared = context.raw("stroke_order");
        if (declared != null && !declared.isBlank()) {
            return declared.trim();
        }
        String type = context.type();
        if (type.equals("hexcasting:manual_pattern")) {
            return "true";
        }
        if (type.equals("hexcasting:manual_pattern_nosig")) {
            return "false";
        }
        return null;
    }

    private static void attribute(StringBuilder tag, String name, String value) {
        if (value != null && !value.isBlank()) {
            tag.append(' ').append(name).append("=\"")
                    .append(value.trim().replace("\"", "&quot;"))
                    .append('"');
        }
    }

    /**
     * The page's input and output, spelled exactly as Patchouli's pattern template spells them.
     *
     * <p>They are literal type expressions ({@code "entity | null"}) rather than language keys,
     * and the template always draws the arrow with both slots whether or not they are filled —
     * {@code get_caster} takes nothing and still reads {@code → entity | null}. The pattern
     * component puts the result under the hexagon, which is where the original puts it.</p>
     */
    private static String signature(PageRenderContext context) {
        String input = context.raw("input");
        String output = context.raw("output");
        boolean hasInput = input != null && !input.isBlank();
        boolean hasOutput = output != null && !output.isBlank();
        if (!hasInput && !hasOutput) {
            return "";
        }
        return (hasInput ? input.trim() : "") + SIGNATURE_ARROW + (hasOutput ? output.trim() : "");
    }
}
