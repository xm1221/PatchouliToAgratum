package cn.xm1221.pta;

import cn.xm1221.pta.core.book.BookSource;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The books this run actually mirrors: every Patchouli book found, minus the excluded mods.
 *
 * <p>One place decides this so that the generated documents, the guide item's creative entries and
 * the export command can never disagree about what is being mirrored.</p>
 *
 * <p>Everything here reads mod files and configuration only — no resource manager, no level — so it
 * is safe to call while the game is still assembling its resource packs.</p>
 */
public final class PtaBookList {
    private static final Logger LOGGER = LogUtils.getLogger();

    private PtaBookList() {
    }

    /**
     * @return the mirrored book ids, sorted by id for a stable order between runs
     */
    public static List<ResourceLocation> effectiveBooks() {
        Set<String> excluded;
        try {
            // This also runs on a dedicated server, where a config that could not be loaded
            // throws instead of returning its defaults. Mirroring everything is the same answer
            // as "no exclusions", and it is the one that cannot silently lose a book.
            excluded = PtaConfig.excludedNamespaces();
        } catch (RuntimeException exception) {
            LOGGER.warn("[pta] could not read the excluded mods; mirroring every book found",
                    exception);
            excluded = Set.of();
        }

        Set<ResourceLocation> seen = new LinkedHashSet<>();
        List<ResourceLocation> result = new ArrayList<>();
        for (ResourceLocation bookId : discoverBooks()) {
            if (!seen.add(bookId)) {
                continue;
            }
            if (excluded.contains(bookId.getNamespace())) {
                LOGGER.info("[pta] not mirroring {}: {} is in the exclude list",
                        bookId, bookId.getNamespace());
                continue;
            }
            result.add(bookId);
        }
        result.sort(Comparator.comparing(ResourceLocation::toString));
        return result;
    }

    /**
     * Finds every book Patchouli could load.
     *
     * <p>Books are looked for in mods' own files, at {@code data/<namespace>/patchouli_books/}
     * — the path a book's files live at. Patchouli itself finds books through the resource
     * manager, but this runs while that manager is still being assembled (the guides are what it
     * is assembling), so the files are read directly instead. A book that only exists in a
     * datapack or another pack is not found here, and never was: the guide is generated from the
     * book's files, not from Patchouli's loaded copy.</p>
     */
    private static List<ResourceLocation> discoverBooks() {
        List<ResourceLocation> found = new ArrayList<>();
        for (var info : ModList.get().getMods()) {
            String namespace = info.getModId();
            Path root = modRoot(namespace);
            if (root == null) {
                continue;
            }
            Path books = root.resolve("data/" + namespace + "/" + BookSource.NAMESPACE_BOOKS);
            if (!Files.isDirectory(books)) {
                continue;
            }
            try (Stream<Path> entries = Files.list(books)) {
                entries.filter(Files::isDirectory)
                        .map(path -> path.getFileName().toString())
                        .map(name -> ResourceLocation.fromNamespaceAndPath(namespace, name))
                        .forEach(found::add);
            } catch (IOException exception) {
                LOGGER.debug("[pta] could not list books of {}", namespace, exception);
            }
        }
        return found;
    }

    /**
     * A mod's own file root, which is where its {@code data/} and {@code assets/} live.
     *
     * <p>For a mod in a jar this is a path inside that jar's own filesystem, so listing it works
     * the same as listing a directory in a development run.</p>
     */
    private static Path modRoot(String namespace) {
        var modFileInfo = ModList.get().getModFileById(namespace);
        if (modFileInfo == null) {
            return null;
        }
        try {
            return modFileInfo.getFile().getSecureJar().getRootPath();
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
