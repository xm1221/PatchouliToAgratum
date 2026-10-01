package cn.xm1221.pta.client.render;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.HolderLookup;
import org.jetbrains.annotations.Nullable;
import vazkii.patchouli.api.IComponentRenderContext;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.UnaryOperator;

/**
 * Draws Hex Casting's spell patterns without depending on Hex Casting.
 *
 * <p>The pattern is a vector drawn by the mod that owns it, and re-drawing it here would mean
 * tracking its pattern maths, its colours and its stroke-order hints forever. So the two component
 * classes Hex Casting registers with Patchouli are loaded <b>by name</b> and driven through
 * reflection: this mod compiles and runs whether or not Hex Casting is installed, and pattern
 * pages simply report that they need it when it is not.</p>
 *
 * <p>Only Patchouli is referenced directly, because it is the one hard dependency.</p>
 *
 * <p>Two of Hex Casting's passes are reproduced, in this order, because that is the order
 * Patchouli itself uses:</p>
 * <ol>
 *   <li>{@code onVariablesAvailable(...)}, which turns the raw action id or pattern string into
 *       the actual patterns; the registry provider it takes is unused by both components.</li>
 *   <li>{@code build(x, y, hexSize)}, which only decides where inside the component to draw —
 *       {@code (0, 0)} puts the hexagon at the component's own origin.</li>
 * </ol>
 */
public final class HexPatternBridge {
    private static final String LOOKUP = "at.petrak.hexcasting.interop.patchouli.LookupPatternComponent";
    private static final String MANUAL = "at.petrak.hexcasting.interop.patchouli.ManualPatternComponent";
    private static final String ABSTRACT = "at.petrak.hexcasting.interop.patchouli.AbstractPatternComponent";

    /** Everything is resolved once, on first use, and never retried. */
    private static boolean resolved;
    private static @Nullable String failure;

    private static @Nullable Class<?> lookupClass;
    private static @Nullable Class<?> manualClass;
    private static @Nullable Method onVariablesAvailable;
    private static @Nullable Method build;
    private static @Nullable Method render;
    private static @Nullable Field opNameRaw;
    private static @Nullable Field patternsRaw;
    private static @Nullable Field strokeOrderRaw;

    private HexPatternBridge() {
    }

    /**
     * @return why patterns cannot be drawn, or {@code null} when Hex Casting is present and usable
     */
    public static @Nullable String failure() {
        resolve();
        return failure;
    }

    /**
     * Builds a component that draws the pattern of one action.
     *
     * @param opId       the action id, e.g. {@code hexcasting:get_caster}
     * @param registries the client's registries, or {@code null} before a level is loaded
     * @throws IllegalStateException when Hex Casting is missing or its API has changed shape
     */
    public static Object lookup(String opId, @Nullable HolderLookup.Provider registries) {
        resolve();
        require();

        Object component = instantiate(lookupClass);
        set(opNameRaw, component, opId);
        prepare(component, registries);
        return component;
    }

    /**
     * Builds a component that draws a pattern written out in the page itself.
     *
     * @param patterns    the compact pattern list, e.g. {@code "SOUTH_EAST:aqaae"} or
     *                    {@code "NORTH_EAST:qaq;EAST:qaq@2,0"}; see {@link #patternsJson(String)}
     * @param strokeOrder whether to hint the stroke order, or {@code null} for the default
     * @param registries  the client's registries, or {@code null} before a level is loaded
     * @throws IllegalStateException when Hex Casting is missing or its API has changed shape
     */
    public static Object manual(String patterns, @Nullable Boolean strokeOrder,
                                @Nullable HolderLookup.Provider registries) {
        resolve();
        require();

        Object component = instantiate(manualClass);
        set(patternsRaw, component, patternsJson(patterns));
        if (strokeOrder != null) {
            set(strokeOrderRaw, component, strokeOrder.toString());
        }
        prepare(component, registries);
        return component;
    }

    /**
     * Turns the converter's compact pattern list into the JSON text Hex Casting parses.
     *
     * <p>{@code ManualPatternComponent} takes its patterns as a string, wraps it in a Patchouli
     * variable and unpacks that into {@code {startdir, signature, q, r}} objects — which only works
     * because Patchouli parses a string that is valid JSON as JSON. Passing text that a Markdown
     * attribute can hold, and building the JSON here, keeps the converter free of quote escaping
     * and keeps Hex Casting's shape in one place.</p>
     *
     * <p>Accepted forms: {@code DIR:SIGNATURE[@q,r]} with {@code ;} between patterns, a bare angle
     * signature (which Hex Casting reads as starting east), or JSON as it stands.</p>
     */
    static String patternsJson(String patterns) {
        String value = patterns.trim();
        if (value.startsWith("[") || value.startsWith("{")) {
            return value;
        }

        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        for (String item : value.split(";")) {
            String token = item.trim();
            if (token.isEmpty()) {
                continue;
            }

            String direction = "EAST";
            int colon = token.indexOf(':');
            if (colon > 0) {
                direction = token.substring(0, colon).trim();
                token = token.substring(colon + 1).trim();
            }

            int q = 0;
            int r = 0;
            int at = token.indexOf('@');
            if (at >= 0) {
                String offsets = token.substring(at + 1).trim();
                token = token.substring(0, at).trim();
                int comma = offsets.indexOf(',');
                q = parseOffset(comma < 0 ? offsets : offsets.substring(0, comma));
                r = comma < 0 ? 0 : parseOffset(offsets.substring(comma + 1));
            }

            if (!first) {
                json.append(',');
            }
            first = false;
            json.append("{\"startdir\":\"").append(escape(direction))
                    .append("\",\"signature\":\"").append(escape(token))
                    .append("\",\"q\":").append(q).append(",\"r\":").append(r).append('}');
        }
        return json.append(']').toString();
    }

    private static int parseOffset(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "").replace("\"", "");
    }

    /**
     * Draws the pattern at the current pose's origin.
     *
     * <p>Hex Casting's render is passed a {@code null} component context: its bytecode reads only
     * {@link GuiGraphics}, and everything else it needs is its own geometry and colours. Should a
     * future version start using that context, the resulting exception is caught by the caller and
     * shown as an error rather than crashing the guide.</p>
     */
    public static void render(Object pattern, GuiGraphics graphics, float mouseX, float mouseY) {
        require();
        try {
            render.invoke(pattern, graphics, null, 0F, (int) mouseX, (int) mouseY);
        } catch (InvocationTargetException exception) {
            throw new IllegalStateException(message(exception.getCause()));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(message(exception));
        }
    }

    private static void prepare(Object component, @Nullable HolderLookup.Provider registries) {
        try {
            onVariablesAvailable.invoke(component, UnaryOperator.identity(), registries);
            build.invoke(component, 0, 0, 1);
        } catch (InvocationTargetException exception) {
            throw new IllegalStateException(message(exception.getCause()));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(message(exception));
        }
    }

    /**
     * A field, public or not.
     */
    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        try {
            return type.getField(name);
        } catch (NoSuchFieldException exception) {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        }
    }

    private static Object instantiate(@Nullable Class<?> type) {        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(message(exception));
        }
    }

    private static void set(@Nullable Field field, Object target, Object value) {
        try {
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(message(exception));
        }
    }

    private static void require() {
        if (failure != null) {
            throw new IllegalStateException(failure);
        }
    }

    private static String message(@Nullable Throwable cause) {
        if (cause == null) {
            return "Hex Casting 的图案组件抛出异常";
        }
        String detail = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        return cause.getClass().getSimpleName() + "：" + detail;
    }

    private static synchronized void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            lookupClass = Class.forName(LOOKUP);
            manualClass = Class.forName(MANUAL);
            Class<?> abstractClass = Class.forName(ABSTRACT);
            onVariablesAvailable = abstractClass.getMethod("onVariablesAvailable",
                    UnaryOperator.class, HolderLookup.Provider.class);
            build = abstractClass.getMethod("build", int.class, int.class, int.class);
            render = abstractClass.getMethod("render", GuiGraphics.class,
                    IComponentRenderContext.class, float.class, int.class, int.class);
            // Patchouli populates these components from page JSON through public fields; the
            // declared-field fallback is there in case one of them is ever made private.
            opNameRaw = field(lookupClass, "opNameRaw");
            patternsRaw = field(manualClass, "patternsRaw");
            strokeOrderRaw = field(manualClass, "strokeOrderRaw");
        } catch (ClassNotFoundException exception) {
            failure = "未安装 Hex Casting，无法绘制图案";
        } catch (ReflectiveOperationException | LinkageError exception) {
            failure = "Hex Casting 的图案组件接口已变化（" + message(exception) + "）";
        }
    }
}
