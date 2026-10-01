package cn.xm1221.pta.client.source;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * A resource pack that serves the mirrored Patchouli guides.
 *
 * <p>Doing this as a pack rather than by intercepting Ageratum's cache scan is what makes the
 * documents real: every lookup in Ageratum goes through the resource manager, not just the scan
 * that parses them. {@code GuideDocumentLoader.exists} answers the command's "is there an entry
 * point?" question by asking the manager directly, so a cache-only injection leaves the command
 * reporting "Guide file not found" while every document loaded perfectly.</p>
 *
 * <p>The documents are generated in the constructor, so each time the pack repository is rebuilt
 * for a reload the content is regenerated from the installed books. A new instance is created per
 * open, so nothing goes stale.</p>
 */
public final class PtaGuidePack implements PackResources {
    public static final String PACK_ID = "pta_mirrored_guides";

    public static final PackLocationInfo LOCATION = new PackLocationInfo(
            PACK_ID,
            Component.literal("Patchouli to Ageratum"),
            PackSource.BUILT_IN,
            Optional.empty()
    );

    private final Map<ResourceLocation, String> documents;

    public PtaGuidePack() {
        Map<ResourceLocation, String> generated;
        try {
            generated = new LinkedHashMap<>(PtaGuideDocuments.build());
        } catch (Throwable throwable) {
            // A failure here happens while the game is assembling its resource packs, where an
            // exception would take the whole reload down. Serving nothing is the correct response:
            // the guide simply is not mirrored this time.
            LogUtils.getLogger().error("[pta] could not generate the mirrored guides", throwable);
            generated = Map.of();
        }
        this.documents = generated;
    }

    /** How many resources this pack is serving, for diagnostics. */
    public int documentCount() {
        return this.documents.size();
    }

    @Override
    public @Nullable IoSupplier<InputStream> getRootResource(String... elements) {
        return null;
    }

    @Override
    public @Nullable IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        if (type != PackType.CLIENT_RESOURCES) {
            return null;
        }
        String text = this.documents.get(location);
        return text == null ? null : () -> new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        if (type != PackType.CLIENT_RESOURCES) {
            return;
        }
        String prefix = path.endsWith("/") ? path : path + "/";
        for (Map.Entry<ResourceLocation, String> document : this.documents.entrySet()) {
            ResourceLocation location = document.getKey();
            if (!location.getNamespace().equals(namespace) || !location.getPath().startsWith(prefix)) {
                continue;
            }
            String text = document.getValue();
            output.accept(location, () -> new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        if (type != PackType.CLIENT_RESOURCES) {
            return Set.of();
        }
        Set<String> namespaces = new TreeSet<>();
        for (ResourceLocation location : this.documents.keySet()) {
            namespaces.add(location.getNamespace());
        }
        return namespaces;
    }

    @Override
    public <T> @Nullable T getMetadataSection(MetadataSectionSerializer<T> serializer) {
        return null;
    }

    @Override
    public PackLocationInfo location() {
        return LOCATION;
    }

    @Override
    public void close() {
        // Nothing to release: the documents are plain strings.
    }

    /** A map view for tests and diagnostics. */
    public Map<ResourceLocation, String> documents() {
        return new LinkedHashMap<>(this.documents);
    }
}
