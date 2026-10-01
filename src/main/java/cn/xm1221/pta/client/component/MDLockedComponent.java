package cn.xm1221.pta.client.component;

import cn.xm1221.pta.client.lock.PtaLocks;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.resource.ageratum.client.feat.markdown.MDExtensionContext;
import dev.anvilcraft.resource.ageratum.client.feat.markdown.MDRenderContext;
import dev.anvilcraft.resource.ageratum.client.feat.markdown.component.MDComponent;
import dev.anvilcraft.resource.ageratum.client.feat.markdown.component.MDTextComponent;
import dev.anvilcraft.resource.ageratum.client.feat.markdown.component.extend.MDNoticeBoxComponent;
import dev.anvilcraft.resource.ageratum.client.feat.markdown.component.extend.MDNoticeBoxComponent.NoticeType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Ageratum component that shows a document only once its advancement is earned.
 *
 * <pre>{@code
 * <pta:locked advancements="minecraft:story/root">
 *   ...
 * </pta:locked>
 *
 * <pta:locked advancements="hexcasting:root,hexcasting:mind_flay" unlock="any" secret="true">
 *   ...
 * </pta:locked>
 * }</pre>
 *
 * <p>Locking cannot be decided while the guide is generated: whether an advancement is earned is per
 * player and changes while the game runs, and Ageratum parses each document once per resource
 * reload. So a document carries the requirement and this component decides while it is drawn —
 * which also means a player who earns the advancement sees the page open without reloading
 * anything.</p>
 *
 * <p>A chapter's lock is looser than an entry's, because Patchouli locks a chapter only while every
 * entry inside it is locked: {@code unlock="any"} opens the chapter as soon as one of the listed
 * advancements is earned. {@code secret} hides the requirement itself, which is what Patchouli does
 * for a secret entry rather than naming what would unlock it.</p>
 *
 * <p>The body arrives as this component's children, so it is drawn here rather than by the guide
 * screen's own loop. That loop is mirrored exactly — the pose advanced by each block's height plus
 * {@value #BLOCK_GAP}, the mouse and the offset kept local to the block, the full line handed to it
 * as its width — because a container that placed or measured its children differently would quietly
 * change the page. Ageratum's {@code <row>}, for instance, hands a child its <i>preferred</i> width,
 * which left-aligns anything that centres itself, such as a pattern or an image; and drawing every
 * child at the pose's origin, as one might expect the offsets to have placed them, stacks the whole
 * body on its first line.</p>
 */
public class MDLockedComponent extends MDComponent {
    /**
     * What a guide screen advances between the blocks of a document by.
     *
     * <p>Mirrored so that a body inside this component is laid out exactly as it would have been
     * without the lock around it.</p>
     */
    private static final int BLOCK_GAP = 5;

    /** The parsed document body, drawn once the lock is open. */
    private final List<MDComponent> body;
    private final List<String> advancements;

    /**
     * What the book called those advancements, in the same order.
     *
     * <p>Used to name a requirement the client cannot name itself — an advancement it has never
     * been told about, which only the document knows about.</p>
     */
    private final List<String> names;

    /** Whether one advancement is enough, as a locked chapter needs. */
    private final boolean unlockAny;

    /** Whether the requirement itself is a secret. */
    private final boolean secret;

    /** Built while locked; the notice is a single block that does not change. */
    private @Nullable MDComponent notice;

    private MDLockedComponent(List<MDComponent> body, List<String> advancements, List<String> names,
                              boolean unlockAny, boolean secret) {
        super(FormattedText.EMPTY);
        this.body = body;
        this.advancements = advancements;
        this.names = names;
        this.unlockAny = unlockAny;
        this.secret = secret;
    }

    /**
     * Parses {@code <pta:locked advancements="..." names="..." unlock="any" secret="true"> ... }
     * {@code </pta:locked>}.
     *
     * <p>A lock with no requirement shows its body: it would never open, and an incomplete tag
     * should not cost a reader the page.</p>
     */
    public static MDComponent parse(MDExtensionContext context) {
        List<String> advancements = split(context.params().get("advancements"), ",");
        if (advancements.isEmpty()) {
            advancements = split(context.params().get("advancement"), ",");
        }
        List<String> names = split(context.params().get("names"), "|");
        if (names.isEmpty()) {
            names = split(context.params().get("name"), "|");
        }
        boolean unlockAny = "any".equalsIgnoreCase(trimmed(context.params().get("unlock")));
        boolean secret = Boolean.parseBoolean(trimmed(context.params().get("secret")));
        List<MDComponent> body = context.renderedContent();
        return new MDLockedComponent(body == null ? List.of() : List.copyOf(body), advancements, names,
                unlockAny, secret);
    }

    /** Splits a repeated attribute, dropping blanks and keeping the order. */
    private static List<String> split(@Nullable String value, String separator) {
        List<String> found = new ArrayList<>();
        if (value == null) {
            return found;
        }
        for (String part : value.split(Pattern.quote(separator))) {
            String trimmed = trimmed(part);
            if (trimmed != null && !found.contains(trimmed)) {
                found.add(trimmed);
            }
        }
        return found;
    }

    private static @Nullable String trimmed(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Whether the player has earned enough of what this lock waits for. */
    private boolean open() {
        if (this.advancements.isEmpty()) {
            return true;
        }
        if (this.unlockAny) {
            for (String advancement : this.advancements) {
                if (PtaLocks.earned(advancement)) {
                    return true;
                }
            }
            return false;
        }
        for (String advancement : this.advancements) {
            if (!PtaLocks.earned(advancement)) {
                return false;
            }
        }
        return true;
    }

    private MDComponent notice() {
        if (this.notice == null) {
            StringBuilder text = new StringBuilder(translate("pta.lock.locked"));
            if (this.secret) {
                text.append(' ').append(translate("pta.lock.secret"));
            } else if (!this.advancements.isEmpty()) {
                text.append('\n').append(Component.translatable("pta.lock.requires",
                        String.join(", ", requirementNames())).getString());
            }
            this.notice = new MDNoticeBoxComponent(NoticeType.WARNING,
                    List.of(new MDTextComponent(text.toString())));
        }
        return this.notice;
    }

    /**
     * What to call each requirement: the name the achievement screen shows, or — for an advancement
     * the client was never told about — the name the book itself wrote into the document.
     */
    private List<String> requirementNames() {
        List<String> named = new ArrayList<>(this.advancements.size());
        for (int index = 0; index < this.advancements.size(); index++) {
            String advancement = this.advancements.get(index);
            String name = PtaLocks.requirementName(advancement);
            if (name.equals(advancement) && index < this.names.size()) {
                name = this.names.get(index);
            }
            named.add(name);
        }
        return named;
    }

    private static String translate(String key) {
        return Component.translatable(key).getString();
    }

    /** How tall one body block is at this width. */
    private static int heightOf(Minecraft minecraft, MDComponent block, int width) {
        return block.getHeight(minecraft, width, Integer.MAX_VALUE);
    }

    @Override
    public void render(MDRenderContext context) {
        if (!open()) {
            notice().render(context);
            return;
        }
        Minecraft minecraft = context.minecraft();
        int width = Math.max(0, context.maxX());
        PoseStack pose = context.graphics().pose();
        int top = 0;
        pose.pushPose();
        try {
            for (MDComponent block : this.body) {
                // A block is placed by the pose, not by its offset: components draw at the origin
                // of whatever pose they are handed, and the guide screen only advances the pose
                // between blocks. The offset and the mouse are still the block's own, though —
                // they are what hit tests, tooltips and scissors are measured against.
                pose.pushPose();
                block.render(context.child(width, Integer.MAX_VALUE, context.mouseX(),
                        context.mouseY() - top, context.offsetX(), context.offsetY() + top,
                        context.scale()));
                pose.popPose();
                int step = heightOf(minecraft, block, width) + BLOCK_GAP;
                pose.translate(0, step, 0);
                top += step;
            }
        } finally {
            pose.popPose();
        }
    }

    @Override
    public int getHeight(Minecraft minecraft, int width, int height) {
        if (!open()) {
            return notice().getHeight(minecraft, width, height);
        }
        int total = 0;
        for (MDComponent block : this.body) {
            total += heightOf(minecraft, block, width) + BLOCK_GAP;
        }
        return total;
    }

    @Override
    public int getPreferredWidth(Minecraft minecraft, int width, int height) {
        if (!open()) {
            return notice().getPreferredWidth(minecraft, width, height);
        }
        // The body is prose, which asks for the whole line.
        return width;
    }

    @Override
    public Style getStyleAtPosition(Minecraft minecraft, double x, double y, int width) {
        if (!open()) {
            return notice().getStyleAtPosition(minecraft, x, y, width);
        }
        Block hit = blockAt(minecraft, y, width);
        return hit == null ? null : hit.block().getStyleAtPosition(minecraft, x, hit.y(), width);
    }

    @Override
    public boolean mouseScrolled(Minecraft minecraft, double x, double y, double delta, int width) {
        return delegate(minecraft, x, y, width,
                (block, at, w) -> block.mouseScrolled(minecraft, x, at, delta, w));
    }

    @Override
    public boolean mouseClicked(Minecraft minecraft, double x, double y, int button, int width) {
        return delegate(minecraft, x, y, width,
                (block, at, w) -> block.mouseClicked(minecraft, x, at, button, w));
    }

    @Override
    public boolean mouseDragged(Minecraft minecraft, double x, double y, int button, double dragX,
                                double dragY, int width) {
        return delegate(minecraft, x, y, width,
                (block, at, w) -> block.mouseDragged(minecraft, x, at, button, dragX, dragY, w));
    }

    @Override
    public boolean mouseReleased(Minecraft minecraft, double x, double y, int button, int width) {
        return delegate(minecraft, x, y, width,
                (block, at, w) -> block.mouseReleased(minecraft, x, at, button, w));
    }

    @Override
    public boolean keyPressed(Minecraft minecraft, double x, double y, int keyCode, int scanCode,
                              int modifiers, int width) {
        if (!open()) {
            return notice().keyPressed(minecraft, x, y, keyCode, scanCode, modifiers, width);
        }
        Block hit = blockAt(minecraft, y, width);
        return hit != null && hit.block().keyPressed(minecraft, x, hit.y(), keyCode, scanCode,
                modifiers, width);
    }

    @Override
    public boolean blocksParentKeyHandling(Minecraft minecraft, double x, double y, int keyCode,
                                           int scanCode, int modifiers, int width) {
        if (!open()) {
            return notice().blocksParentKeyHandling(minecraft, x, y, keyCode, scanCode, modifiers, width);
        }
        Block hit = blockAt(minecraft, y, width);
        return hit != null && hit.block().blocksParentKeyHandling(minecraft, x, hit.y(), keyCode,
                scanCode, modifiers, width);
    }

    /** Sends a mouse event to the body block under this y, as the guide screen does for a block. */
    private boolean delegate(Minecraft minecraft, double x, double y, int width, HitAction action) {
        if (!open()) {
            return false;
        }
        Block hit = blockAt(minecraft, y, width);
        return hit != null && action.apply(hit.block(), hit.y(), width);
    }

    /**
     * The body block whose box holds this y, with the y measured from that block's own top.
     *
     * <p>The guide screen resolves a mouse position against a document the same way, so a link or an
     * item inside a locked — and therefore wrapped — page is clicked exactly as it would be
     * anywhere else.</p>
     */
    private @Nullable Block blockAt(Minecraft minecraft, double y, int width) {
        int top = 0;
        for (MDComponent block : this.body) {
            int height = heightOf(minecraft, block, width);
            if (y >= top && y < top + height) {
                return new Block(block, y - top);
            }
            top += height + BLOCK_GAP;
        }
        return null;
    }

    private record Block(MDComponent block, double y) {
    }

    @FunctionalInterface
    private interface HitAction {
        boolean apply(MDComponent block, double y, int width);
    }
}
