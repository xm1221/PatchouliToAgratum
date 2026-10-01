package cn.xm1221.pta.core.text;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reproduces Patchouli's macro expansion.
 *
 * <p>Patchouli does not parse macros. It repeatedly runs plain
 * {@link String#replace(CharSequence, CharSequence)} over the whole text for every macro in
 * turn, stopping when a pass changes nothing, with a hard cap of ten passes
 * ({@code BookTextParser#expandMacros}). Anything else diverges from the real renderer, so
 * this class copies that behaviour exactly — including the fact that a macro's replacement may
 * itself contain macros, which is what makes the fixpoint loop necessary.</p>
 *
 * <h2>Ordering</h2>
 * <p>Patchouli stores macros in a {@link java.util.HashMap}, so the order in which they are
 * applied within a pass is unspecified. This implementation uses insertion order instead:
 * Patchouli's built-ins first, then the book's own macros, with a book macro that reuses a
 * built-in name replacing it in place. That matters for prefix-overlapping macros — Hex
 * Casting declares {@code _Hexcasters} before {@code _Hexcaster}, and applying the singular
 * first would corrupt every plural.</p>
 *
 * <p>Expansion is not delimited: a macro key is replaced wherever it appears, so a macro named
 * {@code hex} would also rewrite the middle of an ordinary word. That is Patchouli's behaviour
 * and it is why books choose unprefixed keys starting with {@code _} or {@code $}.</p>
 */
public final class MacroExpander {
    /** {@code BookTextParser#expandMacros} stops here and logs a warning. */
    public static final int DEFAULT_EXPANSION_CAP = 10;

    private static final Map<String, String> PATCHOULI_DEFAULTS = defaults();

    private final Map<String, String> macros;
    private final int cap;

    private MacroExpander(Map<String, String> macros, int cap) {
        this.macros = macros;
        this.cap = cap;
    }

    /**
     * Builds an expander for one book.
     *
     * @param bookMacros the {@code macros} object of {@code book.json}, may be {@code null}
     */
    public static MacroExpander of(Map<String, String> bookMacros) {
        return of(bookMacros, DEFAULT_EXPANSION_CAP);
    }

    /**
     * Builds an expander with an explicit cap, for tests.
     *
     * @param bookMacros the {@code macros} object of {@code book.json}, may be {@code null}
     */
    public static MacroExpander of(Map<String, String> bookMacros, int cap) {
        Map<String, String> merged = new LinkedHashMap<>(PATCHOULI_DEFAULTS);
        if (bookMacros != null) {
            for (Map.Entry<String, String> entry : bookMacros.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    merged.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return new MacroExpander(Collections.unmodifiableMap(merged), cap);
    }

    /**
     * The macros in the order they will be applied.
     */
    public Map<String, String> macros() {
        return this.macros;
    }

    /**
     * Expands macros until the text stops changing or the cap is hit.
     *
     * @param text raw text, may be {@code null}
     * @return the expanded text plus whether it reached a fixpoint
     */
    public Expansion expand(String text) {
        String current = text == null ? "" : text;
        for (int pass = 0; pass < this.cap; pass++) {
            String next = current;
            for (Map.Entry<String, String> macro : this.macros.entrySet()) {
                if (next.indexOf(macro.getKey()) < 0) {
                    continue;
                }
                next = next.replace(macro.getKey(), macro.getValue());
            }
            if (next.equals(current)) {
                return new Expansion(current, true, pass);
            }
            current = next;
        }
        return new Expansion(current, false, this.cap);
    }

    /**
     * The result of an expansion.
     *
     * @param text       the expanded text
     * @param converged  whether a pass produced no change before the cap was reached
     * @param iterations how many passes actually changed something
     */
    public record Expansion(String text, boolean converged, int iterations) {
        /** Convenience for callers that only want the text. */
        public String orThrow() {
            if (!this.converged) {
                throw new IllegalStateException(
                        "macros did not reach a fixpoint in " + this.iterations + " passes; "
                                + "the book probably has a circular macro");
            }
            return this.text;
        }
    }

    private static Map<String, String> defaults() {
        // Order and contents copied from Book.DEFAULT_MACROS.
        Map<String, String> defaults = new LinkedHashMap<>();
        // The missing closing parenthesis is intentional: it lets $(list2), $(list3) work.
        defaults.put("$(list", "$(li");
        defaults.put("/$", "$()");
        defaults.put("<br>", "$(br)");
        defaults.put("$(item)", "$(#b0b)");
        defaults.put("$(thing)", "$(#490)");
        return Collections.unmodifiableMap(defaults);
    }
}
