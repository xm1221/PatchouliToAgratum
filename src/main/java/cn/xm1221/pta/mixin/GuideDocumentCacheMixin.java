package cn.xm1221.pta.mixin;

import cn.xm1221.pta.client.source.PtaGuideSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Feeds the mirrored Patchouli documents into Ageratum's guide cache.
 *
 * <p>Ageratum has no extension point for document sources: its reload listener scans the resource
 * manager and parses whatever it finds. The cleanest injection is the scan itself, so this
 * redirects the {@code listResources} call that builds the document set and merges in the
 * documents generated from the installed Patchouli books.</p>
 *
 * <p>The target is an anonymous inner class, so it is addressed by name rather than by field
 * reference. That makes this the most version-sensitive piece of the mod: if Ageratum ever
 * restructures its cache, the injection fails and is meant to fail loudly rather than silently
 * produce no guides.</p>
 *
 * <p>Doing this with a resource pack instead was considered and rejected: a pack has to be able
 * to enumerate its own contents, and at the point packs are discovered the resource manager does
 * not exist yet, so the first launch would serve nothing.</p>
 */
@Mixin(targets = "dev.anvilcraft.resource.ageratum.client.feat.markdown.GuideDocumentCache$1")
public class GuideDocumentCacheMixin {
    /**
     * @param manager      the manager being scanned
     * @param path         the directory the cache listed, e.g. {@code ageratum}
     * @param filter       the cache's own predicate, re-applied to the generated documents
     * @return the listed resources plus the mirrored ones
     */
    @Redirect(
            method = "prepare",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/packs/resources/ResourceManager;"
                            + "listResources(Ljava/lang/String;Ljava/util/function/Predicate;)"
                            + "Ljava/util/Map;"
            )
    )
    private Map<ResourceLocation, Resource> pta$addMirroredDocuments(
            ResourceManager manager, String path, Predicate<ResourceLocation> filter) {
        Map<ResourceLocation, Resource> listed = manager.listResources(path, filter);
        Map<ResourceLocation, Resource> merged = new LinkedHashMap<>(listed);
        // putIfAbsent, not putAll: a document that really exists in a resource pack is a deliberate
        // statement and outranks anything this mod synthesises. Without this, mirroring a book
        // into a namespace that also ships hand-written guides would silently replace them.
        PtaGuideSource.documents(manager, path, filter).forEach(merged::putIfAbsent);
        return merged;
    }
}
