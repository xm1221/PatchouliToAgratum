package cn.xm1221.pta.core.book.page;

/**
 * Turns one Patchouli page into Markdown.
 *
 * <p>Registered per page type in a {@link PageTypeRegistry}. A renderer may decline by returning
 * {@code null}, which lets the next candidate — ultimately the host fallback that hands the page
 * to Patchouli — take over. Declining is the right answer for anything a renderer does not fully
 * understand: a half-converted page is worse than one Patchouli draws itself.</p>
 *
 * <p>Implementations must not throw for malformed page data. Producing a note about the page is
 * preferable to losing the whole document.</p>
 */
@FunctionalInterface
public interface PageRenderer {
    /**
     * @param context the page and everything needed to render it
     * @return Markdown for the page, or {@code null} to decline
     */
    String render(PageRenderContext context);
}
