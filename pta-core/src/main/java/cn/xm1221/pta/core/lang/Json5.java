package cn.xm1221.pta.core.lang;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A tolerant JSON5 reader, written for Patchouli language files.
 *
 * <p>It exists because no off-the-shelf Java JSON5 library survives the real files. Modded
 * language files in the wild are hand-edited and contain, all at once:</p>
 * <ul>
 *   <li>{@code //} and {@code /* *}{@code /} comments</li>
 *   <li>bare (unquoted) keys, and quotes that are not closed before a newline</li>
 *   <li>trailing commas</li>
 *   <li>backslash line continuations, both inside strings and between tokens</li>
 *   <li>unescaped double quotes inside double-quoted values, e.g. {@code "say "hi" now"}</li>
 *   <li>CRLF line endings</li>
 * </ul>
 *
 * <p>The unescaped-quote case is the reason this is hand written. A quote only closes a string
 * when what follows it looks like the end of a member (a comma, a closing brace or bracket, a
 * newline, or a comment); otherwise it is treated as content. That is a heuristic, but it is
 * the one that matches how these files are actually written.</p>
 *
 * <p>Values are returned as {@link Map}, {@link List}, {@link String}, {@link Double},
 * {@link Boolean} or {@code null}.</p>
 */
public final class Json5 {
    private final String source;
    private int pos;

    private Json5(String source) {
        this.source = source;
    }

    /**
     * Parses a whole document.
     *
     * @return the root value, or {@code null} when the text is empty
     * @throws IllegalArgumentException when the document cannot be read at all
     */
    public static Object parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("text is null");
        }
        Json5 parser = new Json5(text);
        parser.skipTrivia();
        if (parser.pos >= parser.source.length()) {
            return null;
        }
        return parser.parseValue();
    }

    /**
     * Parses a document and flattens it into the dotted-key form Minecraft uses at runtime.
     *
     * <p>{@code {"a": {"b": {"c": "x"}}}} becomes {@code a.b.c=x}, which is the form produced
     * by flattening the nested source file the way a Gradle lang task would.</p>
     */
    public static Map<String, String> flatten(String text) {
        Map<String, String> out = new LinkedHashMap<>();
        flattenInto("", parse(text), out);
        return out;
    }

    private static void flattenInto(String prefix, Object node, Map<String, String> out) {
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                flattenInto(prefix.isEmpty() ? key : prefix + "." + key, entry.getValue(), out);
            }
        } else if (node instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                flattenInto(prefix + "." + i, list.get(i), out);
            }
        } else if (node != null) {
            out.put(prefix, stringify(node));
        }
    }

    private static String stringify(Object value) {
        if (value instanceof Double d) {
            return d == Math.rint(d) && !d.isInfinite() ? String.valueOf(d.longValue()) : d.toString();
        }
        return String.valueOf(value);
    }

    // ---------------------------------------------------------------- parsing

    private Object parseValue() {
        skipTrivia();
        if (this.pos >= this.source.length()) {
            return null;
        }
        char c = this.source.charAt(this.pos);
        return switch (c) {
            case '{' -> parseObject();
            case '[' -> parseArray();
            case '"', '\'' -> parseString(c, false);
            case '}', ']' -> throw new IllegalArgumentException(
                    "unexpected '" + c + "' at offset " + this.pos);
            default -> parseBare();
        };
    }

    private Map<String, Object> parseObject() {
        expect('{');
        Map<String, Object> map = new LinkedHashMap<>();
        while (true) {
            skipTrivia();
            if (this.pos >= this.source.length()) {
                break;
            }
            char c = this.source.charAt(this.pos);
            if (c == '}') {
                this.pos++;
                break;
            }
            if (c == ',') {
                this.pos++;
                continue;
            }

            String key = parseKey();
            if (key == null) {
                // Nothing that looks like a key: skip one character so we cannot spin forever.
                this.pos++;
                continue;
            }
            skipTrivia();
            if (this.pos < this.source.length() && this.source.charAt(this.pos) == ':') {
                this.pos++;
            }
            Object value = parseValue();
            map.put(key, value);

            skipTrivia();
            if (this.pos < this.source.length() && this.source.charAt(this.pos) == ',') {
                this.pos++;
            }
        }
        return map;
    }

    private List<Object> parseArray() {
        expect('[');
        List<Object> list = new ArrayList<>();
        while (true) {
            skipTrivia();
            if (this.pos >= this.source.length()) {
                break;
            }
            char c = this.source.charAt(this.pos);
            if (c == ']') {
                this.pos++;
                break;
            }
            if (c == ',') {
                this.pos++;
                continue;
            }
            list.add(parseValue());
            skipTrivia();
            if (this.pos < this.source.length() && this.source.charAt(this.pos) == ',') {
                this.pos++;
            }
        }
        return list;
    }

    /**
     * Reads an object key. Quoted keys go through the string reader; bare keys run to the
     * colon, which is the only place a bare key can end.
     *
     * @return the key, or {@code null} when the position does not hold one
     */
    private String parseKey() {
        char c = this.source.charAt(this.pos);
        if (c == '"' || c == '\'') {
            // Keys are simple: they end at the first unescaped quote. The closing heuristic
            // used for values would fail here, because a key's closing quote is followed by a
            // colon rather than by a comma or brace.
            return parseString(c, true);
        }
        int start = this.pos;
        while (this.pos < this.source.length()) {
            char current = this.source.charAt(this.pos);
            if (current == ':' || current == ',' || current == '}' || current == '\n') {
                break;
            }
            this.pos++;
        }
        String key = this.source.substring(start, this.pos).trim();
        return key.isEmpty() ? null : key;
    }

    private String parseString(char quote, boolean keyContext) {
        this.pos++;
        StringBuilder out = new StringBuilder();
        while (this.pos < this.source.length()) {
            char c = this.source.charAt(this.pos);
            if (c == '\\') {
                if (readEscape(out)) {
                    continue;
                }
                break;
            }
            if (c == quote && (keyContext || closesString(this.pos))) {
                this.pos++;
                return out.toString();
            }
            out.append(c);
            this.pos++;
        }
        return out.toString();
    }

    /**
     * Consumes one backslash escape, including line continuations.
     *
     * @return {@code true} when an escape was consumed, {@code false} at end of input
     */
    private boolean readEscape(StringBuilder out) {
        int length = this.source.length();
        if (this.pos + 1 >= length) {
            this.pos++;
            return false;
        }
        char next = this.source.charAt(this.pos + 1);
        if (next == '\n') {
            this.pos += 2;
            return true;
        }
        if (next == '\r') {
            this.pos += 2;
            if (this.pos < length && this.source.charAt(this.pos) == '\n') {
                this.pos++;
            }
            return true;
        }

        this.pos += 2;
        switch (next) {
            case 'n' -> out.append('\n');
            case 't' -> out.append('\t');
            case 'r' -> out.append('\r');
            case 'b' -> out.append('\b');
            case 'f' -> out.append('\f');
            case 'u' -> {
                if (this.pos + 4 <= length) {
                    try {
                        out.append((char) Integer.parseInt(this.source.substring(this.pos, this.pos + 4), 16));
                        this.pos += 4;
                    } catch (NumberFormatException ignored) {
                        out.append("\\u");
                    }
                } else {
                    out.append("\\u");
                }
            }
            default -> out.append(next);
        }
        return true;
    }

    /**
     * Decides whether a quote at {@code quotePos} terminates the string, as opposed to being
     * an unescaped quote inside the content.
     */
    private boolean closesString(int quotePos) {
        int p = quotePos + 1;
        int length = this.source.length();
        while (p < length && (this.source.charAt(p) == ' ' || this.source.charAt(p) == '\t'
                || this.source.charAt(p) == '\r')) {
            p++;
        }
        if (p >= length) {
            return true;
        }
        char c = this.source.charAt(p);
        return c == ',' || c == '}' || c == ']' || c == '\n' || c == '/';
    }

    /**
     * Reads a bare token: a number, {@code true}/{@code false}/{@code null}, or an unquoted
     * string. Bare values end at a delimiter, a newline or the start of a comment.
     */
    private Object parseBare() {
        int start = this.pos;
        int length = this.source.length();
        while (this.pos < length) {
            char c = this.source.charAt(this.pos);
            if (c == ',' || c == '}' || c == ']' || c == '\n') {
                break;
            }
            if (c == '/' && this.pos + 1 < length) {
                char next = this.source.charAt(this.pos + 1);
                if (next == '/' || next == '*') {
                    break;
                }
            }
            this.pos++;
        }
        String token = this.source.substring(start, this.pos).trim();
        if (token.isEmpty()) {
            return null;
        }
        if (token.equals("true")) {
            return Boolean.TRUE;
        }
        if (token.equals("false")) {
            return Boolean.FALSE;
        }
        if (token.equals("null")) {
            return null;
        }
        try {
            return Double.valueOf(token);
        } catch (NumberFormatException ignored) {
            return token;
        }
    }

    private void expect(char expected) {
        if (this.pos >= this.source.length() || this.source.charAt(this.pos) != expected) {
            throw new IllegalArgumentException(
                    "expected '" + expected + "' at offset " + this.pos);
        }
        this.pos++;
    }

    /**
     * Skips whitespace, comments, and backslash line continuations between tokens.
     */
    private void skipTrivia() {
        int length = this.source.length();
        while (this.pos < length) {
            char c = this.source.charAt(this.pos);
            if (Character.isWhitespace(c)) {
                this.pos++;
                continue;
            }
            if (c == '/' && this.pos + 1 < length) {
                char next = this.source.charAt(this.pos + 1);
                if (next == '/') {
                    this.pos += 2;
                    while (this.pos < length && this.source.charAt(this.pos) != '\n') {
                        this.pos++;
                    }
                    continue;
                }
                if (next == '*') {
                    this.pos += 2;
                    while (this.pos + 1 < length
                            && !(this.source.charAt(this.pos) == '*' && this.source.charAt(this.pos + 1) == '/')) {
                        this.pos++;
                    }
                    this.pos = Math.min(length, this.pos + 2);
                    continue;
                }
            }
            if (c == '\\') {
                int p = this.pos + 1;
                while (p < length && (this.source.charAt(p) == ' ' || this.source.charAt(p) == '\t'
                        || this.source.charAt(p) == '\r')) {
                    p++;
                }
                if (p < length && this.source.charAt(p) == '\n') {
                    this.pos = p + 1;
                    continue;
                }
            }
            break;
        }
    }
}
