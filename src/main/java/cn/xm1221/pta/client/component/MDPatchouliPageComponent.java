package cn.xm1221.pta.client.component;

import cn.xm1221.pta.client.render.PatchouliPageHost;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.resource.ageratum.client.feat.markdown.MDExtensionContext;
import dev.anvilcraft.resource.ageratum.client.feat.markdown.MDRenderContext;
import dev.anvilcraft.resource.ageratum.client.feat.markdown.component.MDComponent;
import dev.anvilcraft.resource.ageratum.client.feat.markdown.component.MDTextComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Ageratum block component that renders one page of a Patchouli book.
 *
 * <pre>{@code
 * <pta:page book="hexcasting:thehexbook" entry="patterns/basics" page="0"/>
 * <pta:page book="hexcasting:thehexbook" entry="patterns/basics" page="0" chrome="true"/>
 * }</pre>
 *
 * <p>The real {@link vazkii.patchouli.client.book.BookPage} is constructed and drawn by
 * Patchouli itself, so every page type works — including the templates other mods ship, such
 * as Hex Casting's {@code hexcasting:pattern} pages with their own component processors.
 * This mod never has to know about any of them.</p>
 *
 * <p>By default only the page body is drawn, which needs no clipping. Passing
 * {@code chrome="true"} additionally draws the surrounding book page and paper texture, but
 * that renders 272x180 into a 116x156 box and therefore has to be clipped.</p>
 */
public class MDPatchouliPageComponent extends MDComponent {
    private final PatchouliPageHost host;
    private final boolean chrome;

    public MDPatchouliPageComponent(ResourceLocation bookId, ResourceLocation entryId, int pageIndex,
                                    boolean chrome) {
        super(FormattedText.EMPTY);
        this.chrome = chrome;
        this.host = new PatchouliPageHost(bookId, entryId, pageIndex, !chrome);
    }

    @Override
    public int getPreferredWidth(Minecraft minecraft, int maxX, int maxY) {
        return this.host.width();
    }

    @Override
    public int getHeight(Minecraft minecraft, int maxX, int maxY) {
        return this.host.height();
    }

    @Override
    public void render(MDRenderContext context) {
        Minecraft minecraft = context.minecraft();
        if (!this.host.ensure(minecraft)) {
            this.renderError(context, this.host.error());
            return;
        }

        GuiGraphics graphics = context.graphics();
        PoseStack pose = graphics.pose();
        pose.pushPose();
        try {
            if (!this.chrome) {
                // Nothing but the page is drawn, so there is nothing to clip away.
                this.host.renderPageOnly(graphics, context.mouseX(), context.mouseY());
                return;
            }

            // Chrome mode also draws the 272x180 book around the 116x156 page, so it has to be
            // clipped to the page box.
            //
            // WARNING: this is the least reliable part of the component, and it is *not* the
            // default for that reason. MDRenderContext.enableScissor() converts component-local
            // coordinates to screen space by adding offsetX/offsetY/leftPos and then dividing by
            // MDRenderContext.scale(). That value comes from
            // GuideScreen#scale = window.getGuiScale() / calculateScale (GuideScreen.java:346),
            // which is not 1 in general. In 1.21.1 GuiGraphics.enableScissor() ignores the pose
            // entirely and takes absolute GUI-space coordinates, so nothing compensates for that
            // division: whenever scale < 1 the rectangle grows, moves down-right, and shears the
            // top-left corner off the page — exactly the "top of the book page is missing"
            // symptom. Page-only mode sidesteps the whole question by not needing a scissor.
            context.enableScissor(0, 0, this.host.width(), this.host.height());
            try {
                this.host.renderChrome(graphics, context.mouseX(), context.mouseY());
            } finally {
                context.disableScissor();
            }
        } finally {
            pose.popPose();
        }
    }

    private void renderError(MDRenderContext context, @Nullable String message) {
        String text = message == null ? "Patchouli 页面不可用" : message;
        context.graphics().drawString(context.minecraft().font, text, 0, 0, 0xFF5555, false);
    }

    /**
     * Parses {@code <pta:page book="..." entry="..." page="0" chrome="false"/>}.
     *
     * @return the component, or an inline error component when the parameters are unusable
     */
    public static MDComponent parse(MDExtensionContext context) {
        String rawBook = context.params().get("book");
        String rawEntry = context.params().get("entry");
        if (rawBook == null || rawBook.isBlank()) {
            return new MDTextComponent("[错误：pta:page 需要 book 参数]");
        }
        if (rawEntry == null || rawEntry.isBlank()) {
            return new MDTextComponent("[错误：pta:page 需要 entry 参数]");
        }

        ResourceLocation bookId;
        ResourceLocation entryId;
        try {
            // "hexcasting:thehexbook" is the book id as-is; the entry is addressed by its path
            // inside the book, which is how Patchouli keys entries internally.
            bookId = ResourceLocation.parse(rawBook);
            entryId = rawEntry.contains(":")
                    ? ResourceLocation.parse(rawEntry)
                    : ResourceLocation.fromNamespaceAndPath(bookId.getNamespace(), rawEntry);
        } catch (RuntimeException exception) {
            return new MDTextComponent("[错误：pta:page 的 book/entry 不是合法的资源位置]");
        }

        int pageIndex = 0;
        String rawPage = context.params().get("page");
        if (rawPage != null && !rawPage.isBlank()) {
            try {
                pageIndex = Integer.parseInt(rawPage);
            } catch (NumberFormatException exception) {
                return new MDTextComponent("[错误：pta:page 的 page 参数仅接受整数]");
            }
        }

        boolean chrome = Boolean.parseBoolean(context.params().getOrDefault("chrome", "false"));
        return new MDPatchouliPageComponent(bookId, entryId, pageIndex, chrome);
    }
}
