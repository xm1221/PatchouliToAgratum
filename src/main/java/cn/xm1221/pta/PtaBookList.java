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
 * The books this run actually mirrors: the allow list (or every book found) minus the excluded
 * mods.
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
        List<ResourceLocation> candidates;
        try {
            List<ResourceLocation> configured = PtaConfig.books();
            candidates = configured == null ? discoverBooks() : configured;
        } catch (RuntimeException exception) {
            LOGGER.warn("[pta] could not read the configured book list", exception);
            return List.of();
        }

        Set<String> excluded = PtaConfig.excludedNamespaces();
        Set<ResourceLocation> seen = new LinkedHashSet<>();
        List<ResourceLocation> result = new ArrayList<>();
        for (ResourceLocation bookId : candidates) {
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
     * Finds every book Patchouli could load, for the {@code *} configuration.
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
     * <p>This is the lookup Patchouli uses on NeoForge to find its books.</p>
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
