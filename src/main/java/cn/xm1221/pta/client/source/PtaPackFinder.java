package cn.xm1221.pta.client.source;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.world.flag.FeatureFlags;
import net.neoforged.neoforge.event.AddPackFindersEvent;

import java.util.List;

/**
 * Registers the mirrored-guides pack with the game.
 *
 * <p>The pack sits at the bottom of the stack, so a document that really exists in a resource pack
 * outranks a synthesised one. Without that, mirroring a book into a namespace that also ships
 * hand-written guides would silently replace them.</p>
 */
public final class PtaPackFinder {
    private PtaPackFinder() {
    }

    /**
     * Handles {@link AddPackFindersEvent} on the mod event bus.
     *
     * @param event the pack discovery event, fired once per pack type during startup
     */
    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }
        event.addRepositorySource(consumer -> consumer.accept(new Pack(
                PtaGuidePack.LOCATION,
                new Pack.ResourcesSupplier() {
                    @Override
                    public net.minecraft.server.packs.PackResources openPrimary(
                            net.minecraft.server.packs.PackLocationInfo info) {
                        return new PtaGuidePack();
                    }

                    @Override
                    public net.minecraft.server.packs.PackResources openFull(
                            net.minecraft.server.packs.PackLocationInfo info, Pack.Metadata metadata) {
                        return new PtaGuidePack();
                    }
                },
                new Pack.Metadata(
                        Component.literal("Patchouli books mirrored into Ageratum guides"),
                        PackCompatibility.COMPATIBLE,
                        FeatureFlags.VANILLA_SET,
                        List.of(),
                        // Hidden: it is always active and cannot be moved, so listing it in the
                        // resource pack screen would only be noise.
                        true
                ),
                new PackSelectionConfig(true, Pack.Position.BOTTOM, false)
        )));
    }
}
