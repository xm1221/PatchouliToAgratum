package cn.xm1221.pta;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point of Patchouli to Ageratum.
 *
 * <p>The mod has two halves:</p>
 * <ul>
 *   <li>{@code pta-core} — a pure-JVM converter that turns Patchouli book JSON plus its
 *       language files into Ageratum Markdown.</li>
 *   <li>this NeoForge side — a generated resource pack that feeds those Markdown documents
 *       to Ageratum, plus Markdown components that host real Patchouli pages.</li>
 * </ul>
 *
 * <p>Everything renderer-shaped is client only, which is why the constructor is split by
 * {@link Dist}.</p>
 */
@Mod(PtaMod.MOD_ID)
public final class PtaMod {
    public static final String MOD_ID = "pta";
    public static final Logger LOGGER = LoggerFactory.getLogger("PatchouliToAgeratum");

    public PtaMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Patchouli to Ageratum loading on {}", FMLEnvironment.dist);
    }
}
