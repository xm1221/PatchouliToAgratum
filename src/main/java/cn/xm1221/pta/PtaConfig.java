package cn.xm1221.pta;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Client configuration.
 *
 * <p>The mod is generic: it mirrors whichever Patchouli books it is told to. Hex Casting's book
 * is the default because that is what this was built for, but pointing it at any other book is a
 * config edit and a restart away.</p>
 *
 * <p>Two lists decide what gets mirrored, and both are read through {@link PtaBookList}:</p>
 * <ul>
 *   <li>{@code books.mirror} — the allow list, or {@code *} for every book found.</li>
 *   <li>{@code exclude.mods} — mod namespaces to leave alone, subtracted from whatever the allow
 *       list produced. This is the switch for "mirror everything except that one mod".</li>
 * </ul>
 */
public final class PtaConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.ConfigValue<List<? extends String>> BOOKS;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> EXCLUDED_MODS;

    /** A namespace, as opposed to a path: lower case, digits, and the three separators. */
    private static final String NAMESPACE_PATTERN = "[a-z0-9_.-]+";

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment(
                "Patchouli books to mirror into Ageratum guides, as namespace:book.",
                "Use * to mirror every book Patchouli can find.",
                "pta:spike is this mod's own throwaway book, listed so the mirroring can be",
                "exercised without installing anything else. Remove it from the list to hide it.",
                "Changing this takes effect on the next game start: the guides are generated",
                "while the game assembles its resource packs."
        ).push("books");
        BOOKS = builder
                .comment("One entry per book, e.g. hexcasting:thehexbook")
                .defineListAllowEmpty("mirror", List.of("hexcasting:thehexbook", "pta:spike"),
                        () -> "hexcasting:thehexbook", PtaConfig::isValidBookEntry);
        builder.pop();

        builder.comment(
                "Mod namespaces to leave alone: no guide is generated for a book from these mods,",
                "whether it was named in books.mirror or found by *.",
                "This is the switch for mirroring everything except one mod. Example: hexcasting"
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

    private static boolean isValidBookEntry(Object value) {
        if (!(value instanceof String text)) {
            return false;
        }
        return text.equals("*") || ResourceLocation.tryParse(text) != null;
    }

    private static boolean isValidNamespace(Object value) {
        return value instanceof String text && text.matches(NAMESPACE_PATTERN);
    }

    /**
     * @return the configured book ids, or {@code null} when everything should be mirrored
     */
    public static List<ResourceLocation> books() {
        List<ResourceLocation> result = new ArrayList<>();
        for (String entry : BOOKS.get()) {
            if (entry.equals("*")) {
                return null;
            }
            ResourceLocation id = ResourceLocation.tryParse(entry);
            if (id != null) {
                result.add(id);
            }
        }
        return result;
    }

    /**
     * Mod namespaces whose books are never mirrored.
     *
     * @return the excluded namespaces, empty when everything is allowed
     */
    public static Set<String> excludedNamespaces() {
        return new LinkedHashSet<>(EXCLUDED_MODS.get());
    }
}
