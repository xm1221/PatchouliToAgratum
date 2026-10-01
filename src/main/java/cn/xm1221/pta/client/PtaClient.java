package cn.xm1221.pta.client;

import cn.xm1221.pta.PtaComponents;
import cn.xm1221.pta.PtaMod;
import cn.xm1221.pta.client.command.PtaExportCommand;
import cn.xm1221.pta.client.model.PtaClientModels;
import cn.xm1221.pta.client.source.PtaPackFinder;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client-only entry point.
 *
 * <p>Kept separate from {@link PtaMod} so that a dedicated server never loads a class that
 * touches Ageratum's client registries or the mirrored guide pack.</p>
 */
@Mod(value = PtaMod.MOD_ID, dist = Dist.CLIENT)
public final class PtaClient {
    public PtaClient(IEventBus modEventBus, ModContainer modContainer) {
        PtaComponents.register(modEventBus);
        // The mirrored guides are served as a resource pack rather than injected into Ageratum's
        // cache, so that every lookup Ageratum makes - not just its parsing scan - sees them.
        modEventBus.addListener(PtaPackFinder::onAddPackFinders);
        // A guidebook wears the manual it opens, so the books' models are baked even though no
        // item or blockstate points at them, and the guidebook's own model is taught to choose.
        modEventBus.addListener(PtaClientModels::onRegisterAdditional);
        modEventBus.addListener(PtaClientModels::onModifyBakingResult);
        // The export command is a client command: it writes the client's own mirror to the
        // client's own disk, so it is registered on the client's dispatcher and nowhere else.
        NeoForge.EVENT_BUS.addListener(PtaExportCommand::onRegisterClientCommands);
    }
}
