package cn.xm1221.pta.core.book.page;

/**
 * Renders Patchouli's empty pages as nothing.
 *
 * <p>An empty page exists to pad a spread, which is a thing a page-turning book has and a scrolling
 * document does not. Hosting it would draw a faithful blank page in the middle of the guide, so the
 * page is answered with nothing instead.</p>
 */
public final class BlankPageRenderer implements PageRenderer {
    public static final String TYPE = "patchouli:empty";

    @Override
    public String render(PageRenderContext context) {
        return "";
    }
}
