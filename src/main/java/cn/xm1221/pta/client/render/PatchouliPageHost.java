package cn.xm1221.pta.client.render;

import cn.xm1221.pta.PtaMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import vazkii.patchouli.client.base.PersistentData;
import vazkii.patchouli.client.book.BookContents;
import vazkii.patchouli.client.book.BookEntry;
import vazkii.patchouli.client.book.BookPage;
import vazkii.patchouli.client.book.gui.GuiBook;
import vazkii.patchouli.client.book.gui.GuiBookEntry;
import vazkii.patchouli.common.book.Book;
import vazkii.patchouli.common.book.BookRegistry;

import java.util.List;

/**
 * Hosts a single, real {@link BookPage} so it can be drawn inline inside an Ageratum
 * Markdown document.
 *
 * <p>This class is the whole reason the mod does not re-implement a single Patchouli page
 * type: {@link BookPage#render} takes no screen parameter and draws relative to the page
 * object itself, so a detached page can be laid out at an arbitrary origin and rendered as
 * an ordinary widget.</p>
 *
 * <h2>Two modes</h2>
 * <ul>
 *   <li><b>Page only</b> (default) — draws just the page body at the component origin. No
 *       scissor is needed, because nothing else is drawn, so this mode cannot be affected by
 *       clipping maths at all.</li>
 *   <li><b>Chrome</b> — draws the whole Patchouli screen so the surrounding book page and
 *       paper texture show. That draws 272x180 of content into a 116x156 box, so the caller
 *       must clip; see {@link #renderChrome}.</li>
 * </ul>
 *
 * <h2>Why init() is called rather than setupPages()</h2>
 * <p>{@code setupPages()} is private and only runs from {@code init()} / {@code onPageChanged()}.
 * {@code init()} is also the only public way to give the detached screen a {@code Minecraft}
 * instance, which {@link BookPage#onDisplayed} reads through the parent. It additionally
 * reads the player's persisted book GUI scale and may install a scale factor, so that setting
 * is pinned to {@code 0} for the duration of the call.</p>
 */
public final class PatchouliPageHost {
    /** {@code GuiBook.FULL_WIDTH}. */
    private static final int BOOK_WIDTH = 272;
    /** {@code GuiBook.FULL_HEIGHT}. */
    private static final int BOOK_HEIGHT = 180;
    private static final int PAGE_WIDTH = 116;
    private static final int PAGE_HEIGHT = 156;
    private static final int TOP_PADDING = 18;
    private static final int LEFT_PAGE_X = 15;
    private static final int RIGHT_PAGE_X = 141;

    private final ResourceLocation bookId;
    private final ResourceLocation entryId;
    private final int pageIndex;
    private final boolean pageOnly;

    private @Nullable GuiBookEntry gui;
    private @Nullable BookPage page;
    /** Page origin inside the book; only meaningful in chrome mode. */
    private int pageX = LEFT_PAGE_X;
    private boolean resolved;
    private @Nullable String error;

    public PatchouliPageHost(ResourceLocation bookId, ResourceLocation entryId, int pageIndex,
                             boolean pageOnly) {
        this.bookId = bookId;
        this.entryId = entryId;
        this.pageIndex = pageIndex;
        this.pageOnly = pageOnly;
    }

    public int width() {
        return PAGE_WIDTH;
    }

    public int height() {
        return PAGE_HEIGHT;
    }

    /** @return a human readable failure reason, or {@code null} when the host is usable. */
    public @Nullable String error() {
        return this.error;
    }

    /**
     * Resolves the book, entry and page exactly once and builds the detached screen.
     *
     * @return {@code true} when one of the {@code render*} methods may be called
     */
    public boolean ensure(Minecraft minecraft) {
        if (this.resolved) {
            return this.page != null;
        }
        this.resolved = true;

        Book book = BookRegistry.INSTANCE.books.get(this.bookId);
        if (book == null) {
            this.error = "找不到 Patchouli 手册 " + this.bookId;
            return false;
        }

        BookContents contents = book.getContents();
        if (contents == null || contents.isErrored()) {
            this.error = "手册 " + this.bookId + " 的内容尚未就绪";
            return false;
        }

        BookEntry entry = contents.entries.get(this.entryId);
        if (entry == null) {
            this.error = "手册 " + this.bookId + " 中找不到条目 " + this.entryId;
            return false;
        }

        List<BookPage> pages = entry.getPages();
        if (this.pageIndex < 0 || this.pageIndex >= pages.size()) {
            this.error = "条目 " + this.entryId + " 没有第 " + this.pageIndex + " 页（共 "
                    + pages.size() + " 页）";
            return false;
        }

        // A spread always holds the even page on the left and the odd page on the right.
        this.pageX = this.pageIndex % 2 == 0 ? LEFT_PAGE_X : RIGHT_PAGE_X;

        int savedBookGuiScale = 0;
        boolean scaleSaved = false;
        GuiBook previousCurrentGui = contents.currentGui;
        try {
            if (PersistentData.data != null) {
                savedBookGuiScale = PersistentData.data.bookGuiScale;
                // Pinning this to 0 makes GuiBook.init() take its "scaleFactor = 1" branch,
                // so it never touches the window GUI scale.
                PersistentData.data.bookGuiScale = 0;
                scaleSaved = true;
            }

            GuiBookEntry host = new GuiBookEntry(book, entry, this.pageIndex / 2);
            host.init(minecraft, BOOK_WIDTH, BOOK_HEIGHT);
            // init() assigns itself as the book's current GUI for "resume reading"; we are a
            // mirror, not a navigation step, so put the previous one back.
            contents.currentGui = previousCurrentGui;

            BookPage target = pages.get(this.pageIndex);
            if (this.pageOnly) {
                // Place the page at the detached screen's origin so the page's own coordinates
                // line up with the component's. bookLeft/bookTop are already 0 from init().
                target.onDisplayed(host, 0, 0);
            } else {
                host.bookLeft = -this.pageX;
                host.bookTop = -TOP_PADDING;
            }

            this.gui = host;
            this.page = target;
        } catch (Throwable throwable) {
            this.error = "构建 Patchouli 页面失败：" + throwable;
            this.gui = null;
            this.page = null;
            PtaMod.LOGGER.error("Failed to build detached Patchouli page {}#{}",
                    this.entryId, this.pageIndex, throwable);
        } finally {
            if (scaleSaved && PersistentData.data != null) {
                PersistentData.data.bookGuiScale = savedBookGuiScale;
            }
            contents.currentGui = previousCurrentGui;
        }

        return this.page != null;
    }

    /**
     * Draws only the page body. The caller must have translated the pose to the component
     * origin. No clipping is required or performed.
     *
     * @param mouseX component-local mouse X
     * @param mouseY component-local mouse Y
     */
    public void renderPageOnly(GuiGraphics graphics, float mouseX, float mouseY) {
        BookPage target = this.page;
        if (target == null) {
            return;
        }
        // The host is never the active screen, so nothing ticks it. Drive the counter from
        // wall clock time instead: PageSpotlight cycles its item every 20 ticks.
        GuiBookEntry host = this.gui;
        if (host != null) {
            host.ticksInBook = (int) (System.currentTimeMillis() / 50L);
        }
        target.render(graphics, Math.round(mouseX), Math.round(mouseY), 0.0F);
    }

    /**
     * Draws the full Patchouli screen, including the surrounding book page. The caller is
     * expected to have translated the pose to the component origin and clipped to
     * {@link #width()} x {@link #height()}.
     *
     * @param mouseX component-local mouse X
     * @param mouseY component-local mouse Y
     */
    public void renderChrome(GuiGraphics graphics, float mouseX, float mouseY) {
        GuiBookEntry host = this.gui;
        if (host == null) {
            return;
        }
        host.ticksInBook = (int) (System.currentTimeMillis() / 50L);
        // GuiBookEntry subtracts page.left / page.top before handing coordinates to the page,
        // so add our page origin back to get page-local mouse coordinates.
        host.render(graphics, Math.round(mouseX) + this.pageX, Math.round(mouseY) + TOP_PADDING,
                0.0F);
    }
}
