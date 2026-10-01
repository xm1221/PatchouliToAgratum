package cn.xm1221.pta.item;

import cn.xm1221.pta.PtaGuides;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A book that opens a mirrored Patchouli manual as an Ageratum guide.
 *
 * <p>Which book is a data component, so one item covers every book that happens to be mirrored.
 * A stack with no book opens the first mirrored one, which is what makes a plain
 * {@code /give pta:guidebook} useful rather than inert in the usual single-book setup.</p>
 *
 * <p>The book's own appearance is not reproduced by rendering — the item model is generated to
 * wear that book's Patchouli model, so the guidebook looks like the manual it opens instead of
 * like a new item this mod invented.</p>
 */
public class GuideBookItem extends Item {
    public GuideBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            // Opening a guide is a server-to-client payload: the client cannot open a document the
            // server has not told it about.
            PtaGuides.open(serverPlayer, PtaGuides.bookOf(stack));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        ResourceLocation book = PtaGuides.bookOf(stack);
        tooltip.add(Component.translatable("item.pta.guidebook.hint").withStyle(ChatFormatting.GRAY));
        if (book == null) {
            tooltip.add(Component.translatable("item.pta.guidebook.unset").withStyle(ChatFormatting.RED));
        } else {
            tooltip.add(Component.literal(book.toString()).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
