package cn.xm1221.pta;

import cn.xm1221.pta.item.GuideBookItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The items this mod adds.
 *
 * <p>One guidebook, not one per book: the books are runtime data, so a stack carries the book it
 * opens in a component instead of the mod pre-registering an item for every book it might find.
 * The creative tab is what turns that into something a player can actually pick up — one entry per
 * mirrored book, each already carrying its book.</p>
 */
public final class PtaItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PtaMod.MOD_ID);

    /** The one item: a book that opens a mirrored Patchouli manual. */
    public static final DeferredItem<GuideBookItem> GUIDEBOOK =
            ITEMS.registerItem("guidebook", GuideBookItem::new, new Item.Properties().stacksTo(1));

    private PtaItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }

    /**
     * A guidebook for one book.
     *
     * <p>Two things beyond the component are set here, and both exist so that the stack looks and
     * reads like the book it opens: the Patchouli model that book wears (see
     * {@link cn.xm1221.pta.client.source.PtaGuideAssets}), and the book's own name key, which the
     * client already has translated.</p>
     *
     * @param book the book to open, or {@code null} for a guidebook that names none
     */
    public static ItemStack guideStack(@Nullable ResourceLocation book) {
        ItemStack stack = new ItemStack(GUIDEBOOK.get());
        if (book == null) {
            return stack;
        }
        stack.set(PtaDataComponents.GUIDE.get(), book);

        // Model overrides are indexed by position in the mirrored list, which is the order the
        // generated models were built in. Entry 0 wears the base model and needs no override.
        int index = PtaBookList.effectiveBooks().indexOf(book);
        if (index > 0) {
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(index));
        }

        String nameKey = PtaGuides.bookNameKey(book);
        if (nameKey != null) {
            stack.set(DataComponents.ITEM_NAME, Component.translatable(nameKey));
        }
        return stack;
    }

    /**
     * Adds one guidebook per mirrored book to the creative inventory.
     *
     * <p>Runs while the tab contents are being built, which the integrated server does with the
     * client's configuration in hand. A dedicated server has no client configuration to read, so
     * it offers the bare item instead of one per book.</p>
     */
    public static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) {
            return;
        }

        List<ResourceLocation> books = PtaBookList.effectiveBooks();
        if (books.isEmpty()) {
            event.accept(guideStack(null));
            return;
        }
        for (ResourceLocation book : books) {
            event.accept(guideStack(book));
        }
    }
}
