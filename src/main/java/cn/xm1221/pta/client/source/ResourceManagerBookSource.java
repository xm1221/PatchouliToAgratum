package cn.xm1221.pta.client.source;

import cn.xm1221.pta.core.book.BookSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Adapts Minecraft's {@link ResourceManager} to the converter's {@link BookSource}.
 *
 * <p>The converter addresses files by their full resource path, {@code data/<ns>/...} or
 * {@code assets/<ns>/...}, because that is how they sit on disk. The resource manager addresses
 * the same file as {@code <ns>:<path after assets/ or data/>}. This class translates between the
 * two, so everything downstream stays free of Minecraft types.</p>
 */
public final class ResourceManagerBookSource implements BookSource {
    private final ResourceManager manager;

    public ResourceManagerBookSource(ResourceManager manager) {
        this.manager = manager;
    }

    @Override
    public String read(String resourcePath) {
        String normalized = resourcePath.replace('\\', '/');
        if (normalized.startsWith("data/")) {
            // book.json lives under data/, and a client resource manager only serves assets/.
            // Patchouli reads it from the mod file too, for the same reason.
            return readFromModFile(normalized);
        }
        Optional<ResourceLocation> location = toResourceLocation(normalized);
        if (location.isEmpty()) {
            return null;
        }
        try (InputStream stream = this.manager.open(location.get())) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            // A missing file is the normal case here, not an error.
            return null;
        }
    }

    /**
     * Reads a {@code data/} resource straight out of the owning mod's file.
     *
     * <p>The path is used whole, {@code data/} included, because that prefix is a real directory
     * inside the mod file. Patchouli reads its books from the mod file for the same reason: a
     * client resource manager only serves {@code assets/}.</p>
     *
     * <p>Several lookups are tried because a mod is a directory in a development environment and
     * a jar in production, and the two are not reachable the same way.</p>
     */
    private static String readFromModFile(String resourcePath) {
        String remainder = resourcePath.substring("data/".length());
        int slash = remainder.indexOf('/');
        if (slash <= 0) {
            return null;
        }
        String namespace = remainder.substring(0, slash);

        var modFileInfo = net.neoforged.fml.ModList.get().getModFileById(namespace);
        if (modFileInfo == null) {
            return null;
        }
        var modFile = modFileInfo.getFile();

        List<Path> candidates = new ArrayList<>();
        try {
            // The jar's (or directory's) own root, which is what Patchouli walks to find books.
            Path root = modFile.getSecureJar().getRootPath();
            if (root != null) {
                candidates.add(root.resolve(resourcePath));
            }
        } catch (RuntimeException ignored) {
            // Not every mod file exposes a usable root; the other lookups cover it.
        }
        try {
            Path found = modFile.findResource(resourcePath.split("/"));
            if (found != null) {
                candidates.add(found);
            }
        } catch (RuntimeException ignored) {
            // Same.
        }
        Path filePath = modFile.getFilePath();
        if (filePath != null && Files.isDirectory(filePath)) {
            candidates.add(filePath.resolve(resourcePath));
        }

        for (Path candidate : candidates) {
            try {
                if (Files.isRegularFile(candidate)) {
                    return Files.readString(candidate, StandardCharsets.UTF_8);
                }
            } catch (IOException | RuntimeException ignored) {
                // Try the next candidate.
            }
        }

        if (filePath != null && Files.isRegularFile(filePath)) {
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(filePath.toFile())) {
                java.util.zip.ZipEntry entry = zip.getEntry(resourcePath);
                if (entry != null) {
                    try (InputStream stream = zip.getInputStream(entry)) {
                        return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                    }
                }
            } catch (IOException | IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    @Override
    public List<String> list(String directory, String suffix) {
        Optional<ResourceLocation> probe = toResourceLocation(directory);
        if (probe.isEmpty()) {
            return List.of();
        }
        String namespace = probe.get().getNamespace();
        String path = probe.get().getPath();
        String prefix = path.endsWith("/") ? path : path + "/";

        List<String> result = new ArrayList<>();
        for (ResourceLocation found : this.manager.listResources(path,
                location -> location.getPath().startsWith(prefix) && location.getPath().endsWith(suffix)
        ).keySet()) {
            if (found.getNamespace().equals(namespace)) {
                result.add("assets/" + namespace + "/" + found.getPath());
            }
        }
        result.sort(String::compareTo);
        return result;
    }

    /**
     * Converts a full resource path into the location the manager expects.
     *
     * @param resourcePath a path starting with {@code assets/<ns>/} or {@code data/<ns>/}
     * @return the location, or empty when the path is not a resources path
     */
    private static Optional<ResourceLocation> toResourceLocation(String resourcePath) {
        String normalized = resourcePath.replace('\\', '/');
        String root;
        if (normalized.startsWith("assets/")) {
            root = "assets/";
        } else if (normalized.startsWith("data/")) {
            root = "data/";
        } else {
            return Optional.empty();
        }
        String remainder = normalized.substring(root.length());
        int slash = remainder.indexOf('/');
        if (slash <= 0 || slash == remainder.length() - 1) {
            return Optional.empty();
        }
        String namespace = remainder.substring(0, slash);
        String path = remainder.substring(slash + 1);
        return Optional.of(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    @Override
    public String toString() {
        return "ResourceManagerBookSource[" + this.manager.listPacks().count() + " packs]";
    }
}
