package cn.xm1221.pta.core.book.page;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Maps Patchouli page types onto {@link PageRenderer}s.
 *
 * <p>This is the extension point the conversion is built around: a page type is converted natively
 * when a renderer is registered for it, and handed to Patchouli through {@code <pta:page>}
 * otherwise. The host fallback is deliberately last rather than first — reproducing a page is
 * cheap and exact, but a page rendered as real Markdown is searchable, selectable and linkable,
 * so it is worth converting whatever can be converted faithfully.</p>
 *
 * <p>Lookup is by exact type id with the namespace filled in, so {@code text} and
 * {@code patchouli:text} are the same key. A renderer that returns {@code null} declines, and the
 * fallback runs; that lets one renderer handle a family of types and bail out on the members it
 * does not really understand.</p>
 */
public final class PageTypeRegistry {
    private final Map<String, PageRenderer> renderers = new LinkedHashMap<>();
    private PageRenderer fallback;

    /**
     * A registry with the built-in renderers.
     *
     * <p>Currently: text and link pages become Markdown, every recipe page becomes Ageratum's
     * native recipe component, Hex Casting's pattern pages keep their prose but hand the hexagon
     * to the mod that owns it, and everything else is hosted.</p>
     */
    public static PageTypeRegistry standard() {
        PageTypeRegistry registry = new PageTypeRegistry();
        registry.register("patchouli:text", new TextPageRenderer());
        registry.register("patchouli:link", new LinkPageRenderer());
        PageRenderer recipes = new RecipePageRenderer();
        for (String type : RecipePageRenderer.TYPES) {
            registry.register(type, recipes);
        }
        registry.register(MultiRecipePageRenderer.TYPE, new MultiRecipePageRenderer());
        PageRenderer patterns = new HexPatternPageRenderer();
        for (String type : HexPatternPageRenderer.TYPES) {
            registry.register(type, patterns);
        }
        registry.fallback(new HostPageRenderer());
        return registry;
    }

    /**
     * Registers or replaces the renderer for a page type.
     *
     * @param type     a page type id, with or without its namespace
     * @param renderer the renderer, must not be {@code null}
     */
    public PageTypeRegistry register(String type, PageRenderer renderer) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(renderer, "renderer");
        this.renderers.put(qualify(type), renderer);
        return this;
    }

    /**
     * Sets the renderer used for every type without its own registration.
     *
     * @param renderer the fallback, must not be {@code null}
     */
    public PageTypeRegistry fallback(PageRenderer renderer) {
        this.fallback = Objects.requireNonNull(renderer, "renderer");
        return this;
    }

    /**
     * @return the registered renderer, or {@code null} when the type has none
     */
    public PageRenderer rendererFor(String type) {
        return this.renderers.get(qualify(type));
    }

    /**
     * Renders a page, falling back when the type is unregistered or its renderer declines.
     *
     * @return Markdown for the page, never {@code null}
     */
    public String render(PageRenderContext context) {
        PageRenderer renderer = rendererFor(context.type());
        if (renderer != null) {
            String rendered = renderer.render(context);
            if (rendered != null) {
                return rendered;
            }
        }
        if (this.fallback == null) {
            throw new IllegalStateException("no renderer and no fallback for page type " + context.type());
        }
        String rendered = this.fallback.render(context);
        return rendered == null ? "" : rendered;
    }

    /** The page types with a renderer of their own, for diagnostics. */
    public Map<String, PageRenderer> renderers() {
        return Map.copyOf(this.renderers);
    }

    /** Fills in the default namespace, matching how Patchouli reads a type. */
    private static String qualify(String type) {
        if (type == null || type.isBlank()) {
            return "patchouli:text";
        }
        return type.contains(":") ? type : "patchouli:" + type;
    }
}
