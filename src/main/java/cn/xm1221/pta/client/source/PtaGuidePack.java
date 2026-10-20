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
 * <p>The documents are generated on the first content query, not when the pack is opened. That is
 * deliberate, for two reasons:</p>
 * <ul>
 *   <li>Only then does the resource manager hold the new pack stack — during a reload it is
 *       switched before any listener runs — so the books are read exactly as that reload installed
 *       them, other mods' chapters included.</li>
 *   <li>Opening the pack is not the same as reading it: the resource pack screen opens every pack
 *       to draw its description, and generating a whole book there would be a stall with no
 *       result.</li>
 * </ul>
 *
 * <p>A new instance is created per reload, so the content is regenerated every time the resource
 * packs are reloaded.</p>
 */
public final class PtaGuidePack implements PackResources {
    public static final String PACK_ID = "pta_mirrored_guides";

    public static final PackLocationInfo LOCATION = new PackLocationInfo(
            PACK_ID,
            Component.literal("Patchouli to Ageratum"),
            PackSource.BUILT_IN,
            Optional.empty()
    );

    private volatile Map<ResourceLocation, String> documents;

    /**
     * True while this thread is generating.
     *
     * <p>Generating reads books through the resource manager, and this pack is one of the packs in
     * that manager — so the manager will ask this pack for its resources while they are still being
     * built. Answering "nothing" for that one thread is what keeps it from recursing into itself;
     * other threads simply wait for the lock.</p>
     */
    private final ThreadLocal<Boolean> generating = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public PtaGuidePack() {
    }

    /**
     * The documents, generated once per pack instance.
     *
     * <p>Built under a lock because the reload reads packs from worker threads, and the first query
     * can come from any of them.</p>
     */
    private Map<ResourceLocation, String> built() {
        if (Boolean.TRUE.equals(this.generating.get())) {
            return Map.of();
        }
        Map<ResourceLocation, String> built = this.documents;
        if (built != null) {
            return built;
        }
        synchronized (this) {
            built = this.documents;
            if (built == null) {
                built = generate();
                this.documents = built;
            }
            return built;
        }
    }

    private Map<ResourceLocation, String> generate() {
        this.generating.set(Boolean.TRUE);
        try {
            return new LinkedHashMap<>(PtaGuideDocuments.build());
        } catch (Throwable throwable) {
            // This runs inside a resource reload, where an exception would take the whole reload
            // down. Serving nothing is the correct response: the guide simply is not mirrored this
            // time.
            LogUtils.getLogger().error("[pta] could not generate the mirrored guides", throwable);
            return Map.of();
        } finally {
            this.generating.set(Boolean.FALSE);
        }
    }

    /** How many resources this pack is serving, for diagnostics. */
    public int documentCount() {
        return built().size();
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
        String text = built().get(location);
        return text == null ? null : () -> new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        if (type != PackType.CLIENT_RESOURCES) {
            return;
        }
        String prefix = path.endsWith("/") ? path : path + "/";
        for (Map.Entry<ResourceLocation, String> document : built().entrySet()) {
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
        for (ResourceLocation location : built().keySet()) {
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
        return new LinkedHashMap<>(built());
    }
}
