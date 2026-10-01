package cn.xm1221.pta.client.component;

import cn.xm1221.pta.PtaMod;
import cn.xm1221.pta.client.render.HexPatternBridge;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.resource.ageratum.client.feat.markdown.MDExtensionContext;
import dev.anvilcraft.resource.ageratum.client.feat.markdown.MDRenderContext;
import dev.anvilcraft.resource.ageratum.client.feat.markdown.component.MDComponent;
import dev.anvilcraft.resource.ageratum.client.feat.markdown.component.MDTextComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.FormattedText;
import org.jetbrains.annotations.Nullable;

/**
 * Ageratum component that draws one Hex Casting pattern, optionally captioned with its signature.
 *
 * <pre>{@code
 * <pta:pattern op="hexcasting:get_caster" io="→ entity | null"/>
 * <pta:pattern patterns="aqaa" stroke_order="true"/>
 * }</pre>
 *
 * <p>Everything the reader can select — the heading, the signature, the prose — is Ageratum text
 * converted from the book; only the hexagon is Hex Casting's, and it is drawn by Hex Casting's own
 * component through {@link HexPatternBridge}. Without that mod installed, the component says so and
 * the rest of the page is unaffected.</p>
 *
 * <p>The signature is drawn by this component rather than as a sibling Markdown block so that it
 * lands directly under the hexagon: Ageratum centres a component by letting it measure the line it
 * was given, and a row of "pattern with its signature" beside "prose" would wrap, because a
 * paragraph asks for the whole line.</p>
 */
public class MDHexPatternComponent extends MDComponent {
    /** The box Hex Casting lays a single pattern out in, taken from its own component. */
    private static final int PATTERN_WIDTH = 116;
    private static final int PATTERN_HEIGHT = 64;

    /**
     * How far below the component's own origin Hex Casting draws that box.
     *
     * <p>Its component is written for Patchouli's pattern template, which leaves room above the
     * hexagon, and it draws at {@code translate(x, cellHeight * row + 16, 100)}. Drawing through it
     * without accounting for that puts the hexagon 16px low — low enough to sit on top of the
     * signature drawn under the box — so the pose is pulled back up by the same amount and the box
     * starts at this component's top edge.</p>
     */
    private static final int PATTERN_TOP_OFFSET = 16;

    /** Blank pixels between the bottom of the box and the signature. */
    private static final int CAPTION_GAP = 2;

    private final @Nullable String opId;
    private final @Nullable String patterns;
    private final @Nullable String strokeOrder;
    private final @Nullable MDTextComponent caption;

    /** Built on first render, where a client is available; {@code null} until then. */
    private @Nullable Object pattern;
    private @Nullable String error;

    private MDHexPatternComponent(@Nullable String opId, @Nullable String patterns,
                                  @Nullable String strokeOrder, @Nullable String signature) {
        super(FormattedText.EMPTY);
        this.opId = opId;
        this.patterns = patterns;
        this.strokeOrder = strokeOrder;
        this.caption = signature == null || signature.isBlank() ? null : new MDTextComponent(signature);
    }

    @Override
    public int getPreferredWidth(Minecraft minecraft, int maxX, int maxY) {
        return Math.min(PATTERN_WIDTH, maxX);
    }

    @Override
    public int getHeight(Minecraft minecraft, int maxX, int maxY) {
        int height = PATTERN_HEIGHT + CAPTION_GAP;
        if (this.caption != null) {
            height += this.caption.getHeight(minecraft, getPreferredWidth(minecraft, maxX, maxY), maxY);
        }
        return height;
    }

    @Override
    public void render(MDRenderContext context) {
        Minecraft minecraft = context.minecraft();
        GuiGraphics graphics = context.graphics();
        PoseStack pose = graphics.pose();
        // The hexagon and the signature are centred together, the way Ageratum centres an image:
        // ask for the line's width and take what is left over on both sides.
        int left = Math.max(0, (Math.max(0, context.maxX()) - PATTERN_WIDTH) / 2);
        pose.pushPose();
        try {
            if (!ensure(minecraft)) {
                graphics.drawString(minecraft.font, "图案不可用：" + this.error, 0, 0, 0xFF5555, false);
                return;
            }

            pose.pushPose();
            pose.translate(left, -PATTERN_TOP_OFFSET, 0);
            HexPatternBridge.render(this.pattern, graphics, context.mouseX(), context.mouseY());
            pose.popPose();

            if (this.caption != null) {
                int captionLeft = left
                        + Math.max(0, (PATTERN_WIDTH - minecraft.font.width(this.caption.getText())) / 2);
                pose.translate(captionLeft, PATTERN_HEIGHT + CAPTION_GAP, 0);
                this.caption.render(context);
            }
        } catch (RuntimeException exception) {
            graphics.drawString(minecraft.font, "图案渲染失败：" + exception.getMessage(),
                    0, 0, 0xFF5555, false);
        } finally {
            pose.popPose();
        }
    }

    /**
     * Builds Hex Casting's component once, on the first render.
     *
     * <p>Deferred because the registry provider it wants only exists once a level is loaded, and
     * because a document can be parsed while the guide is still being assembled.</p>
     *
     * @return whether there is a pattern to draw
     */
    private boolean ensure(Minecraft minecraft) {
        if (this.pattern != null) {
            return true;
        }
        if (this.error != null) {
            return false;
        }
        try {
            HolderLookup.Provider registries = minecraft.level == null
                    ? null
                    : minecraft.level.registryAccess();
            this.pattern = this.opId != null
                    ? HexPatternBridge.lookup(this.opId, registries)
                    : HexPatternBridge.manual(this.patterns, parseStrokeOrder(), registries);
            return true;
        } catch (RuntimeException exception) {
            this.error = exception.getMessage();
            // The guide shows this as text, and text is not something anyone can hand over when
            // asking why a pattern is missing — so the stack trace goes to the log as well, with
            // what the page asked for. Tried once: `error` above keeps this out of later frames.
            PtaMod.LOGGER.warn("[pta] cannot draw the pattern of {}: {}",
                    this.opId != null ? "op " + this.opId : "patterns " + this.patterns,
                    this.error, exception);
            return false;
        }
    }

    private @Nullable Boolean parseStrokeOrder() {
        if (this.strokeOrder == null || this.strokeOrder.isBlank()) {
            return null;
        }
        return Boolean.valueOf(this.strokeOrder.trim());
    }

    /**
     * Parses {@code <pta:pattern op="..." patterns="..." stroke_order="..." io="..."/>}.
     *
     * @return the component, or an inline note when the parameters or Hex Casting are unusable
     */
    public static MDComponent parse(MDExtensionContext context) {
        String opId = trimmed(context.params().get("op"));
        String patterns = trimmed(context.params().get("patterns"));
        if (opId == null && patterns == null) {
            return new MDTextComponent("[错误：pta:pattern 需要 op 或 patterns 参数]");
        }

        String missing = HexPatternBridge.failure();
        if (missing != null) {
            return new MDTextComponent("[" + missing + "]");
        }
        return new MDHexPatternComponent(opId, patterns,
                trimmed(context.params().get("stroke_order")), trimmed(context.params().get("io")));
    }

    private static @Nullable String trimmed(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
