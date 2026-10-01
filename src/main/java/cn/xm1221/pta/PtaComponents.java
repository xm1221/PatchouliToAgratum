package cn.xm1221.pta;

import cn.xm1221.pta.client.component.MDHexPatternComponent;
import cn.xm1221.pta.client.component.MDPatchouliPageComponent;
import dev.anvilcraft.resource.ageratum.client.feat.markdown.MDExtensionComponentFactory;
import dev.anvilcraft.resource.ageratum.client.registries.AgeratumRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Ageratum extension components contributed by this mod.
 *
 * <p>Everything here is client-only: Ageratum's registries only exist on the client.</p>
 */
public final class PtaComponents {
    public static final DeferredRegister<MDExtensionComponentFactory> EXTENSION_COMPONENTS =
            DeferredRegister.create(
                    AgeratumRegistries.EXTENSION_COMPONENT_FACTORY_REGISTRY_KEY,
                    PtaMod.MOD_ID
            );

    /**
     * Hosts one real Patchouli page inside a guide document.
     *
     * <pre>{@code <pta:page book="hexcasting:thehexbook" entry="patterns/basics" page="0"/>}</pre>
     */
    public static final DeferredHolder<MDExtensionComponentFactory, MDExtensionComponentFactory> PAGE =
            EXTENSION_COMPONENTS.register("page", () -> MDPatchouliPageComponent::parse);

    /**
     * Draws one Hex Casting pattern, optionally captioned with its signature.
     *
     * <pre>{@code <pta:pattern op="hexcasting:get_caster" io="→ entity | null"/>}</pre>
     *
     * <p>Hex Casting's own component draws the hexagon; see
     * {@link cn.xm1221.pta.client.render.HexPatternBridge}. Installing it is optional.</p>
     */
    public static final DeferredHolder<MDExtensionComponentFactory, MDExtensionComponentFactory> PATTERN =
            EXTENSION_COMPONENTS.register("pattern", () -> MDHexPatternComponent::parse);

    private PtaComponents() {
    }

    public static void register(IEventBus modEventBus) {
        EXTENSION_COMPONENTS.register(modEventBus);
    }
}
