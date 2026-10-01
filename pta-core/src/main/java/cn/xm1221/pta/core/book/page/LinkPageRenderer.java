package cn.xm1221.pta.core.book.page;

/**
 * Renders a {@code patchouli:link} page.
 *
 * <p>A link page is a text page with a button underneath, so it renders its body exactly like one
 * and then adds the target as an ordinary Markdown link. Ageratum has no button, but a link that
 * opens the same place is the useful part of it.</p>
 */
public final class LinkPageRenderer extends TextPageRenderer {
    @Override
    protected void appendExtra(PageRenderContext context, StringBuilder section) {
        String url = context.raw("url");
        if (url == null || url.isBlank()) {
            return;
        }
        String label = context.localised("link_text");
        section.append("\n\n[").append(label.isBlank() ? url : label).append("](").append(url).append(')');
    }
}
