package cn.xm1221.pta.item;

import cn.xm1221.pta.PtaGuides;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A book that opens a mirrored Patchouli manual as an Ageratum guide.
 *
 * <p>Which book is a data component, so one item covers every book that happens to be mirrored.
 * Everything the item shows is derived from that component rather than baked into the stack: the
 * name comes from the book's own language key, and the model is chosen while the stack is being
 * drawn (see {@code GuideBookModel}), so a guidebook handed out by any means looks like the manual
 * it opens.</p>
 *
 * <p>A guidebook with no book in it does nothing. It is not an entrance to "whichever book happens
 * to be mirrored" — that would be a guess, and the first mirrored book is an artefact of the
 * configuration rather than something the player chose.</p>
 */
public class GuideBookItem extends Item {
    public GuideBookItem(Properties properties) {
        super(properties);
    }

    /**
     * The book's own name, in the player's own language.
     *
     * <p>An explicit name component still wins, the way it does for every other item; only when
     * there is none does the book get to name the stack.</p>
     */
    @Override
    public Component getName(ItemStack stack) {
        Component named = stack.get(DataComponents.ITEM_NAME);
        if (named != null) {
            return named;
        }
        ResourceLocation book = PtaGuides.bookOf(stack);
        String key = book == null ? null : PtaGuides.bookNameKey(book);
        return key == null ? Component.translatable(getDescriptionId(stack)) : Component.translatable(key);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        ResourceLocation book = PtaGuides.bookOf(stack);
        if (book == null) {
            return InteractionResultHolder.pass(stack);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            // Opening a guide is a server-to-client payload: the client cannot open a document the
            // server has not told it about.
            PtaGuides.open(serverPlayer, book);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
