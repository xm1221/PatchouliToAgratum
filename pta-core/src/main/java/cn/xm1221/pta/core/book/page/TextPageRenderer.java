package cn.xm1221.pta.core.book.page;

/**
 * Renders a {@code patchouli:text} page as Markdown.
 *
 * <p>The page's own body becomes real Markdown, so it can be selected, searched and linked. Its
 * optional title becomes a section heading; Patchouli shows it above the body in the same way.</p>
 */
public class TextPageRenderer implements PageRenderer {
    @Override
    public String render(PageRenderContext context) {
        StringBuilder section = new StringBuilder();
        String title = context.title();
        if (!title.isBlank()) {
            section.append("## ").append(title).append("\n\n");
        }
        section.append(context.renderText(context.text()));
        appendExtra(context, section);
        return section.toString();
    }

    /**
     * Hook for subclasses that add something after the body.
     *
     * @param context the page being rendered
     * @param section the Markdown built so far
     */
    protected void appendExtra(PageRenderContext context, StringBuilder section) {
        // Nothing by default.
    }
}
