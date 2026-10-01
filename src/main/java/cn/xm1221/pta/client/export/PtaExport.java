package cn.xm1221.pta.client.export;

import cn.xm1221.pta.client.source.PtaGuideDocuments;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Writes the mirrored guides out as files.
 *
 * <p>The documents are generated in memory for the resource pack, and until now they only existed
 * there. An export is the same generation run written to disk in the pack's own layout, so what
 * lands in the folder is not a report about the mirror but the mirror itself: drop
 * {@code assets/} and {@code pack.mcmeta} into a resource pack and the game reads exactly what the
 * guide showed, which is what makes the export useful for reading, diffing and hand-editing
 * without editing a book JSON or restarting.</p>
 */
public final class PtaExport {
    /** Where an export goes when the command names no directory. */
    public static final String DEFAULT_DIRECTORY = "pta-export";

    private PtaExport() {
    }

    /**
     * Writes the guides of some books under a directory.
     *
     * @param root  the directory to write into; created when missing
     * @param books the books to mirror
     * @return how many files were written
     */
    public static int write(Path root, List<ResourceLocation> books) throws IOException {
        PtaGuideDocuments.Build build = PtaGuideDocuments.buildFor(books);

        int written = 0;
        for (Map.Entry<ResourceLocation, String> document : build.documents().entrySet()) {
            ResourceLocation location = document.getKey();
            Path file = inside(root, "assets/" + location.getNamespace() + "/" + location.getPath());
            if (file != null) {
                write(file, document.getValue());
                written++;
            }
        }
        for (Map.Entry<ResourceLocation, String> report : build.reports().entrySet()) {
            ResourceLocation book = report.getKey();
            Path file = inside(root, book.getNamespace() + "/" + book.getPath() + "-conversion-report.md");
            if (file != null) {
                write(file, report.getValue());
                written++;
            }
        }

        // With a pack metadata file beside the assets, the export is a resource pack the game can
        // load as it stands. It is not counted as a document: it is packaging, not content.
        if (written > 0) {
            write(root.resolve("pack.mcmeta"), packMeta());
        }
        return written;
    }

    /**
     * Resolves a path inside the export folder, refusing anything that would leave it.
     *
     * <p>These names come from a stranger's book files. A category or entry name is allowed to
     * contain dots and slashes, so an export must not be a way to write outside the folder it was
     * pointed at.</p>
     *
     * @return the path, or {@code null} when it would escape the export folder
     */
    private static @Nullable Path inside(Path root, String relative) {
        Path file = root.resolve(relative).normalize();
        return file.startsWith(root.normalize()) ? file : null;
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    /** Pack metadata for the current client resource format, so the export loads as a pack. */
    private static String packMeta() {
        int format = SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES);
        return "{\n  \"pack\": {\n    \"pack_format\": " + format
                + ",\n    \"description\": \"Patchouli books mirrored into Ageratum guides\"\n  }\n}\n";
    }
}
