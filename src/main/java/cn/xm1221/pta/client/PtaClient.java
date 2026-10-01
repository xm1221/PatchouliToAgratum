package cn.xm1221.pta.client;

import cn.xm1221.pta.PtaComponents;
import cn.xm1221.pta.PtaMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Client-only entry point.
 *
 * <p>Kept separate from {@link PtaMod} so that a dedicated server never loads a class that
 * touches Ageratum's client registries.</p>
 */
@Mod(value = PtaMod.MOD_ID, dist = Dist.CLIENT)
public final class PtaClient {
    public PtaClient(IEventBus modEventBus, ModContainer modContainer) {
        PtaComponents.register(modEventBus);
    }
}
