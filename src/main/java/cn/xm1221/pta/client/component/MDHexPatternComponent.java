package cn.xm1221.pta.client.component;

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
 * lands directly under the hexagon: Ageratum's rows do not nest, so a two-column layout of
 * "pattern with its signature" beside "prose" has to be one component wide on the left.</p>
 */
public class MDHexPatternComponent extends MDComponent {
    /** The box Hex Casting lays a single pattern out in, taken from its own component. */
    private static final int PATTERN_WIDTH = 116;
    private static final int PATTERN_HEIGHT = 64;

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
        int height = PATTERN_HEIGHT;
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
        pose.pushPose();
        try {
            if (!ensure(minecraft)) {
                graphics.drawString(minecraft.font, "图案不可用：" + this.error, 0, 0, 0xFF5555, false);
                return;
            }

            HexPatternBridge.render(this.pattern, graphics, context.mouseX(), context.mouseY());

            if (this.caption != null) {
                // Centred under the hexagon, matching where Patchouli's pattern template puts it.
                int x = Math.max(0, (PATTERN_WIDTH - minecraft.font.width(this.caption.getText())) / 2);
                pose.translate(x, PATTERN_HEIGHT, 0);
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
