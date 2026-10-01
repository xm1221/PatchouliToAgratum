package cn.xm1221.pta;

import cn.xm1221.pta.core.book.page.PageRenderer;
import cn.xm1221.pta.core.book.page.PageTypeRegistry;

/**
 * How this mod turns Patchouli page types into Ageratum markup, as an extension point.
 *
 * <p>One registry is built at startup and handed to every conversion, so a renderer registered
 * here applies to every book the mod mirrors for the rest of the run. Registering a type that
 * already has a built-in renderer replaces it, and a renderer that returns {@code null} still falls
 * back to hosting the page in Patchouli — a registration can therefore change how a page is shown,
 * but it cannot lose it.</p>
 *
 * <p>Written for other mods, with one caveat: the converter is compiled into this mod rather than
 * published as a library, so a mod that wants to register a renderer compiles against this mod's
 * jar. The interface it implements is {@link PageRenderer}, and the context it is handed is
 * {@code PageRenderContext}, which resolves the book's language keys and converts its
 * {@code $(...)} commands so that a renderer never has to.</p>
 */
public final class PtaPageRenderers {
    private static final PageTypeRegistry REGISTRY = PageTypeRegistry.standard();

    private PtaPageRenderers() {
    }

    /**
     * Registers, or replaces, the renderer for a page type.
     *
     * @param type     a page type id, with or without its namespace
     * @param renderer the renderer
     */
    public static void register(String type, PageRenderer renderer) {
        REGISTRY.register(type, renderer);
    }

    /** The shared registry, for the code that converts books. */
    public static PageTypeRegistry registry() {
        return REGISTRY;
    }
}
