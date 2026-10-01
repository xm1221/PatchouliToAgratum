package cn.xm1221.pta.client.lock;

import com.mojang.logging.LogUtils;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import vazkii.patchouli.client.base.ClientAdvancements;

/**
 * Whether a guide's lock is open, answered the way Patchouli answers it for a book's own locks.
 *
 * <p>The client knows which advancements the player has earned, but only privately: the map lives
 * inside {@code ClientAdvancements} behind a mixin in Patchouli's own code. Rather than duplicate
 * that access, this asks Patchouli itself — it is a required dependency, and a mirrored guide should
 * unlock exactly when the book it came from would.</p>
 *
 * <p>If that call is ever unavailable — a Patchouli version that renamed it, a client class that
 * failed to load — a lock is treated as open instead of closed. A guide that shows something early
 * is a much smaller failure than a guide whose pages can never be read, and the cause is logged
 * once.</p>
 */
public final class PtaLocks {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static boolean unavailable;

    private PtaLocks() {
    }

    /**
     * Whether the player has earned an advancement.
     *
     * @param advancement the advancement's id, as the book declared it
     * @return whether the lock it guards is open; {@code true} when this cannot be determined
     */
    public static boolean earned(@Nullable String advancement) {
        if (advancement == null || advancement.isBlank() || unavailable) {
            return true;
        }
        try {
            return ClientAdvancements.hasDone(advancement.trim());
        } catch (Throwable failure) {
            unavailable = true;
            LOGGER.error("[pta] Patchouli's advancement check is unusable; guide locks stay open",
                    failure);
            return true;
        }
    }

    /**
     * How an advancement reads to the player: the title the advancement shows in the advancement
     * screen, or its id when the client has never seen it.
     */
    public static String requirementName(String advancement) {
        ResourceLocation id = ResourceLocation.tryParse(advancement);
        AdvancementHolder holder = id == null ? null : holder(id);
        if (holder == null) {
            return advancement;
        }
        try {
            return Advancement.name(holder).getString();
        } catch (Throwable failure) {
            return advancement;
        }
    }

    private static @Nullable AdvancementHolder holder(ResourceLocation id) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection == null ? null : connection.getAdvancements().get(id);
    }
}
