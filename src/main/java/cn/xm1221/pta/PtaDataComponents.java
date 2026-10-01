package cn.xm1221.pta;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Data components this mod puts on its own items.
 *
 * <p>There is one guidebook item for every mirrored book, and which book it opens is a component
 * rather than a separate item: the books are discovered at runtime, and inventing an item id per
 * book would make the save data depend on the configuration that happened to be in place when an
 * item was crafted.</p>
 */
public final class PtaDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PtaMod.MOD_ID);

    /**
     * Which Patchouli book a guidebook opens, as that book's own id.
     *
     * <p>Persisted and synchronised, so a book that has been renamed in the config still shows the
     * id it was created with rather than silently opening a different book.</p>
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> GUIDE =
            DATA_COMPONENTS.registerComponentType("guide", builder -> builder
                    .persistent(ResourceLocation.CODEC)
                    .networkSynchronized(ResourceLocation.STREAM_CODEC));

    private PtaDataComponents() {
    }

    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }
}
