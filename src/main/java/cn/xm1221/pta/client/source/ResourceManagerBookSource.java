package cn.xm1221.pta.client.source;

import cn.xm1221.pta.core.book.BookSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads book files through the client's resource manager, which is how Patchouli reads them.
 *
 * <p>A book's content is not the property of the mod that declares it. Patchouli reads
 * {@code assets/<namespace>/patchouli_books/<book>/<language>/...} through the resource manager, so
 * every pack in the stack can add chapters to one book: another mod shipping
 * {@code assets/hexcasting/patchouli_books/thehexbook/en_us/entries/...}, or a resource pack
 * overriding a page. Reading the owning mod's file directly sees none of that — which is why a
 * guide built that way only ever has the original content.</p>
 *
 * <p>{@code book.json} is the exception: it lives under {@code data/}, which a client resource
 * manager does not serve, so that one file (and anything else under {@code data/}) is still read
 * from the mod's own file with {@link ModFileBookSource}, exactly as Patchouli does.</p>
 */
final class ResourceManagerBookSource implements BookSource {
    private static final String ASSETS = "assets/";

    private final ResourceManager resources;
    private final BookSource files = new ModFileBookSource();

    ResourceManagerBookSource(ResourceManager resources) {
        this.resources = resources;
    }

    @Override
    public String read(String resourcePath) {
        ResourceLocation location = assetLocation(resourcePath);
        if (location == null) {
            return this.files.read(resourcePath);
        }
        return this.resources.getResource(location).map(ResourceManagerBookSource::text).orElse(null);
    }

    @Override
    public List<String> list(String directory, String suffix) {
        ResourceLocation prefix = assetLocation(directory);
        if (prefix == null) {
            return this.files.list(directory, suffix);
        }
        List<String> result = new ArrayList<>();
        this.resources.listResources(prefix.getPath(), location -> location.getNamespace().equals(prefix.getNamespace())
                        && location.getPath().endsWith(suffix))
                .keySet()
                .forEach(location -> result.add(ASSETS + location.getNamespace() + "/" + location.getPath()));
        result.sort(String::compareTo);
        return result;
    }

    @Override
    public List<String> namespaces() {
        return List.copyOf(this.resources.getNamespaces());
    }

    /**
     * Turns {@code assets/<namespace>/<path>} into a resource location.
     *
     * @return the location, or {@code null} when the path is not under {@code assets/}
     */
    private static ResourceLocation assetLocation(String resourcePath) {
        String normalized = resourcePath.replace('\\', '/');
        if (!normalized.startsWith(ASSETS)) {
            return null;
        }
        String rest = normalized.substring(ASSETS.length());
        int slash = rest.indexOf('/');
        if (slash <= 0 || slash == rest.length() - 1) {
            return null;
        }
        return ResourceLocation.fromNamespaceAndPath(rest.substring(0, slash), rest.substring(slash + 1));
    }

    private static String text(Resource resource) {
        try (InputStream stream = resource.open()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read " + resource.sourcePackId(), exception);
        }
    }

    @Override
    public String toString() {
        return "ResourceManagerBookSource[" + this.resources.listPacks().count() + " packs]";
    }
}
