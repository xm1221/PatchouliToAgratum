package cn.xm1221.pta.core.book.page;

/**
 * The last resort: hand the page to Patchouli.
 *
 * <p>Emits {@code <pta:page>}, which the mod's Ageratum component resolves back to this very page
 * and renders with Patchouli's own code. Nothing is reproduced by hand, so every page type works —
 * including ones from other mods, such as Hex Casting's template pages and their component
 * processors — at the cost of the body not being selectable or searchable.</p>
 *
 * <p>This is a fallback, not the default. A page that has a renderer should use it: real Markdown
 * is worth more to a reader than a faithful but inert picture of a page.</p>
 */
public final class HostPageRenderer implements PageRenderer {
    @Override
    public String render(PageRenderContext context) {
        context.report().hostedPageType(context.type());

        StringBuilder section = new StringBuilder();
        String title = context.title();
        if (!title.isBlank()) {
            section.append("## ").append(title).append("\n\n");
        }
        section.append("<pta:page book=\"").append(context.layout().bookId())
                .append("\" entry=\"").append(context.entry().path())
                .append("\" page=\"").append(context.pageIndex()).append("\"/>");
        return section.toString();
    }
}
