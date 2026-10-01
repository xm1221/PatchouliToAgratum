package cn.xm1221.pta.client.source;

import cn.xm1221.pta.core.book.BookSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
     */
    private static String readFromModFile(String resourcePath) {
        String remainder = resourcePath.substring("data/".length());
        int slash = remainder.indexOf('/');
        if (slash <= 0) {
            return null;
        }
        String namespace = remainder.substring(0, slash);
        String withinMod = remainder.substring(slash + 1);

        var modFile = net.neoforged.fml.ModList.get().getModFileById(namespace);
        if (modFile == null) {
            return null;
        }
        java.nio.file.Path file = modFile.getFile().findResource(withinMod.split("/"));
        if (file == null || !java.nio.file.Files.isRegularFile(file)) {
            return null;
        }
        try {
            return java.nio.file.Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return null;
        }
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
}
