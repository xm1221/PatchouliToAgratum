package cn.xm1221.pta.core.book.page;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders Patchouli's image pages as Markdown images.
 *
 * <p>Ageratum renders {@code ![alt](namespace:path)} from the texture folder, while Patchouli names
 * its images from the resource pack root and includes the {@code textures/} segment, so the same
 * file is named {@code hexcasting:textures/gui/x.png} in a book and
 * {@code hexcasting:gui/x.png} in a guide. Only the segment is rewritten; the file itself is the
 * one the book shipped.</p>
 *
 * <p>Anything that is not a PNG resource location declines, so an image this cannot name exactly is
 * still drawn by Patchouli rather than shown as a missing texture.</p>
 */
public final class ImagePageRenderer implements PageRenderer {
    public static final String TYPE = "patchouli:image";

    /** The segment Ageratum's image syntax leaves implicit. */
    private static final String TEXTURES = "textures/";

    @Override
    public String render(PageRenderContext context) {
        List<String> images = new ArrayList<>();
        for (String image : context.rawList("images")) {
            String target = target(image);
            if (target == null) {
                return null;
            }
            images.add(target);
        }
        if (images.isEmpty()) {
            return null;
        }

        String heading = context.title();
        String alt = heading.isBlank() ? "image" : heading;
        StringBuilder section = new StringBuilder();
        if (!heading.isBlank()) {
            section.append("## ").append(heading).append("\n\n");
        }
        for (int index = 0; index < images.size(); index++) {
            if (index > 0) {
                section.append("\n\n");
            }
            section.append("![").append(alt).append("](").append(images.get(index)).append(')');
        }

        String text = context.renderText(context.text());
        if (!text.isBlank()) {
            section.append("\n\n").append(text);
        }
        return section.toString();
    }

    /**
     * Rewrites a Patchouli image path into Ageratum's form.
     *
     * @return the path, or {@code null} when this is not a PNG resource location
     */
    private static String target(String image) {
        String text = image.trim();
        int colon = text.indexOf(':');
        if (colon < 0) {
            return null;
        }
        String namespace = text.substring(0, colon);
        String path = text.substring(colon + 1);
        if (path.startsWith(TEXTURES)) {
            path = path.substring(TEXTURES.length());
        }
        if (!path.endsWith(".png") || !Identifiers.isValid(namespace + ":" + path)) {
            return null;
        }
        return namespace + ":" + path;
    }
}
