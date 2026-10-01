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
 * <pta:page book="hexcasting:thehexbook" entry="patterns/basics" page="0" look="plain"/>
 * <pta:page book="hexcasting:thehexbook" entry="patterns/basics" page="0" look="book"/>
 * }</pre>
 *
 * <p>The real {@link vazkii.patchouli.client.book.BookPage} is constructed and drawn by
 * Patchouli itself, so every page type works — including the templates other mods ship, such
 * as Hex Casting's {@code hexcasting:pattern} pages with their own component processors.
 * This mod never has to know about any of them.</p>
 *
 * <p>Three appearances are available; see {@link PatchouliPageHost.Appearance}. The default,
 * {@code plain}, draws only the page body and therefore needs no clipping at all.</p>
 */
public class MDPatchouliPageComponent extends MDComponent {
    private final PatchouliPageHost host;

    public MDPatchouliPageComponent(ResourceLocation bookId, ResourceLocation entryId, int pageIndex,
                                    PatchouliPageHost.Appearance appearance) {
        super(FormattedText.EMPTY);
        this.host = new PatchouliPageHost(bookId, entryId, pageIndex, appearance);
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
            if (this.host.appearance() == PatchouliPageHost.Appearance.BOOK) {
                this.renderBookAppearance(context, graphics);
                return;
            }

            // PLAIN draws exactly the component's own rectangle and nothing else, so there is
            // never anything outside the box to clip away.
            this.host.renderPageOnly(graphics, context.mouseX(), context.mouseY());
        } finally {
            pose.popPose();
        }
    }

    /**
     * The only mode that draws outside the component box, and therefore the only one that has
     * to clip. Kept for comparison, not as the default.
     *
     * <p><b>Warning:</b> this path is unreliable on Ageratum builds where
     * {@code GuideScreen#scale} is not 1. {@code MDRenderContext.enableScissor} converts
     * component-local coordinates to screen space by adding {@code offsetX/offsetY/leftPos}
     * and then <i>dividing</i> by {@code MDRenderContext.scale}, which comes from
     * {@code GuideScreen#scale = window.getGuiScale() / calculateScale}
     * ({@code GuideScreen.java:346}). In 1.21.1 {@code GuiGraphics.enableScissor} ignores the
     * pose and takes absolute GUI-space coordinates, so nothing compensates for that division:
     * whenever scale &lt; 1 the rectangle grows, moves down-right, and shears the top-left
     * corner off the page. The default {@link PatchouliPageHost.Appearance#PLAIN} avoids all
     * of this by drawing nothing that needs clipping.</p>
     */
    private void renderBookAppearance(MDRenderContext context, GuiGraphics graphics) {
        context.enableScissor(0, 0, this.host.width(), this.host.height());
        try {
            this.host.renderBook(graphics, context.mouseX(), context.mouseY());
        } finally {
            context.disableScissor();
        }
    }

    private void renderError(MDRenderContext context, @Nullable String message) {
        String text = message == null ? "Patchouli 页面不可用" : message;
        context.graphics().drawString(context.minecraft().font, text, 0, 0, 0xFF5555, false);
    }

    /**
     * Parses {@code <pta:page book="..." entry="..." page="0" look="paper"/>}.
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

        PatchouliPageHost.Appearance appearance = parseAppearance(context);
        if (appearance == null) {
            return new MDTextComponent("[错误：pta:page 的 look 只接受 plain / book]");
        }
        return new MDPatchouliPageComponent(bookId, entryId, pageIndex, appearance);
    }

    /**
     * Reads {@code look}, falling back to the older boolean {@code chrome} parameter.
     *
     * @return the appearance, or {@code null} when the value is not recognised
     */
    private static PatchouliPageHost.Appearance parseAppearance(MDExtensionContext context) {
        String rawLook = context.params().get("look");
        if (rawLook != null && !rawLook.isBlank()) {
            return switch (rawLook.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "plain" -> PatchouliPageHost.Appearance.PLAIN;
                case "book", "chrome" -> PatchouliPageHost.Appearance.BOOK;
                default -> null;
            };
        }
        // Backwards compatibility with the first iteration of this component.
        boolean chrome = Boolean.parseBoolean(context.params().getOrDefault("chrome", "false"));
        return chrome ? PatchouliPageHost.Appearance.BOOK : PatchouliPageHost.Appearance.PLAIN;
    }
}
