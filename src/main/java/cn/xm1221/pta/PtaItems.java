package cn.xm1221.pta;

import cn.xm1221.pta.item.GuideBookItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

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
     * <p>Only the component is set. The name and the model both come from it while the stack is
     * read or drawn, so a stack made anywhere else reads and looks the same as this one.</p>
     *
     * @param book the book to open
     */
    public static ItemStack guideStack(ResourceLocation book) {
        ItemStack stack = new ItemStack(GUIDEBOOK.get());
        stack.set(PtaDataComponents.GUIDE.get(), book);
        return stack;
    }

    /**
     * Adds one guidebook per mirrored book to the creative inventory.
     *
     * <p>Runs while the tab contents are being built, which the integrated server does with the
     * client's configuration in hand. A book that names no book does nothing, so when nothing is
     * mirrored the tab gets nothing rather than an inert item.</p>
     */
    public static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) {
            return;
        }
        for (ResourceLocation book : PtaBookList.effectiveBooks()) {
            event.accept(guideStack(book));
        }
    }
}
