package cn.xm1221.pta;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Configuration, common to both sides.
 *
 * <p>Which books become guides is not a client-only matter: the guide items and the creative
 * entries are registered from the same list, on code both sides run. It is therefore a common
 * config, so a dedicated server and its players agree on what is being mirrored. (A per-world
 * server config could not do this: it does not exist yet while items are being registered.)</p>
 *
 * <p>Every Patchouli book found is mirrored — that is what "mirror a book" means — so the only
 * switch is {@code exclude.mods}, a list of mod namespaces whose books are left alone.</p>
 */
public final class PtaConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.ConfigValue<List<? extends String>> EXCLUDED_MODS;

    /** A namespace, as opposed to a path: lower case, digits, and the three separators. */
    private static final String NAMESPACE_PATTERN = "[a-z0-9_.-]+";

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment(
                "Mod namespaces to leave alone: no guide is generated for a book from these mods.",
                "Every other Patchouli book found is mirrored, this mod's own pta:spike included.",
                "This is the switch for \"mirror everything except that one mod\". Example: hexcasting",
                "Changing this takes effect on the next game start: the guides are generated",
                "while the game assembles its resource packs."
        ).push("exclude");
        EXCLUDED_MODS = builder
                .comment("One namespace per entry, e.g. hexcasting")
                .defineListAllowEmpty("mods", List.of(), () -> "examplemod",
                        PtaConfig::isValidNamespace);
        builder.pop();

        SPEC = builder.build();
    }

    private PtaConfig() {
    }

    private static boolean isValidNamespace(Object value) {
        return value instanceof String text && text.matches(NAMESPACE_PATTERN);
    }

    /**
     * Mod namespaces whose books are never mirrored.
     *
     * @return the excluded namespaces, empty when everything is mirrored
     */
    public static Set<String> excludedNamespaces() {
        return new LinkedHashSet<>(EXCLUDED_MODS.get());
    }
}
