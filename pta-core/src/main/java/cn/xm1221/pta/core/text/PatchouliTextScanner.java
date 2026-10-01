package cn.xm1221.pta.core.text;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits Patchouli book text into a flat list of {@link TextCommand}s.
 *
 * <p>Run this <b>after</b> {@link MacroExpander}. Patchouli expands macros on the raw string and
 * only then scans for commands, so expanding afterwards would miss macros that a command
 * inserted, and scanning first would leave {@code $(item)}-style colour macros unexpanded.</p>
 *
 * <p>The dispatch order below is the order of {@code BookTextParser.COMMAND_LOOKUPS} and it is
 * load-bearing: functions are looked up <i>before</i> exact command names, which is how the same
 * letter can mean two things — {@code $(k)} is obfuscated text while {@code $(k:use)} is a
 * keybind.</p>
 */
public final class PatchouliTextScanner {
    /**
     * {@code BookTextParser.COMMAND_PATTERN}. Deliberately does not handle nested parentheses;
     * neither does Patchouli, so {@code $(t:a (b) c)} is already broken upstream.
     */
    private static final Pattern COMMAND_PATTERN = Pattern.compile("\\$\\(([^)]*)\\)");

    private static final Pattern COLOR_CODE = Pattern.compile("^[0-9a-f]$");
    private static final Pattern LIST_ITEM = Pattern.compile("li\\d?");
    private static final Pattern EXTERNAL_LINK = Pattern.compile("^https?:.*");

    private PatchouliTextScanner() {
    }

    /**
     * Scans macro-expanded text.
     *
     * @param text text that has already been through {@link MacroExpander}
     * @return the commands in document order; literal runs become {@link TextCommand.Text}
     */
    public static List<TextCommand> scan(String text) {
        List<TextCommand> commands = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return commands;
        }

        Matcher matcher = COMMAND_PATTERN.matcher(text);
        int cursor = 0;
        while (matcher.find()) {
            if (matcher.start() > cursor) {
                commands.add(new TextCommand.Text(text.substring(cursor, matcher.start())));
            }
            commands.add(classify(matcher.group(1)));
            cursor = matcher.end();
        }
        if (cursor < text.length()) {
            commands.add(new TextCommand.Text(text.substring(cursor)));
        }
        return commands;
    }

    private static TextCommand classify(String body) {
        // 1. Legacy colour codes: a single hex digit.
        if (COLOR_CODE.matcher(body).matches()) {
            return new TextCommand.ColorCode(body.charAt(0));
        }

        // 2. Hex colours.
        if (body.startsWith("#") && (body.length() == 4 || body.length() == 7)) {
            return new TextCommand.Color(normaliseHex(body.substring(1)));
        }

        // 3. List items.
        if (LIST_ITEM.matcher(body).matches()) {
            int depth = body.length() > 2 ? Character.digit(body.charAt(2), 10) : 1;
            return new TextCommand.ListItem(Math.max(1, depth));
        }

        // 4. Functions, i.e. anything with a parameter. Wins over exact command names.
        int colon = body.indexOf(':');
        if (colon > 0) {
            return classifyFunction(body.substring(0, colon), body.substring(colon + 1));
        }

        // 5. Exact command names.
        return classifyCommand(body);
    }

    private static TextCommand classifyFunction(String name, String parameter) {
        return switch (name) {
            case "k" -> new TextCommand.Keybind(parameter);
            case "l" -> {
                int hash = parameter.indexOf('#');
                String anchor = null;
                String target = parameter;
                if (hash >= 0) {
                    anchor = parameter.substring(hash + 1);
                    target = parameter.substring(0, hash);
                }
                yield new TextCommand.LinkStart(target, anchor, EXTERNAL_LINK.matcher(parameter).matches());
            }
            case "t", "tooltip" -> new TextCommand.TooltipStart(parameter);
            case "c", "command" -> new TextCommand.CommandStart(parameter);
            // Patchouli renders unknown functions as this marker rather than as text.
            default -> new TextCommand.Text("[MISSING FUNCTION: " + name + "]");
        };
    }

    private static TextCommand classifyCommand(String body) {
        return switch (body) {
            case "br" -> new TextCommand.LineBreak(1);
            case "br2", "2br", "p" -> new TextCommand.LineBreak(2);
            case "/l" -> new TextCommand.LinkEnd();
            case "/t" -> new TextCommand.TooltipEnd();
            case "/c" -> new TextCommand.CommandEnd();
            case "playername" -> new TextCommand.PlayerName();
            case "k", "obf" -> new TextCommand.Format(TextCommand.Format.Kind.OBFUSCATED);
            case "l", "bold" -> new TextCommand.Format(TextCommand.Format.Kind.BOLD);
            case "m", "strike" -> new TextCommand.Format(TextCommand.Format.Kind.STRIKETHROUGH);
            case "n", "underline" -> new TextCommand.Format(TextCommand.Format.Kind.UNDERLINE);
            case "o", "italic", "italics" -> new TextCommand.Format(TextCommand.Format.Kind.ITALIC);
            case "", "reset", "clear" -> new TextCommand.Reset();
            case "nocolor" -> new TextCommand.ResetColor();
            default -> new TextCommand.Unknown(body);
        };
    }

    /**
     * Patchouli accepts {@code #rgb} and {@code #rrggbb}; both become {@code #rrggbb} so the
     * writer has one shape to emit.
     */
    private static String normaliseHex(String hex) {
        if (hex.length() != 3) {
            return "#" + hex.toLowerCase(java.util.Locale.ROOT);
        }
        StringBuilder out = new StringBuilder("#");
        for (int i = 0; i < 3; i++) {
            char c = Character.toLowerCase(hex.charAt(i));
            out.append(c).append(c);
        }
        return out.toString();
    }
}
