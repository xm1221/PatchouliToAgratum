package cn.xm1221.pta.core.book.page;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders Patchouli's spotlight pages as Ageratum's item component.
 *
 * <p>A spotlight is an item with prose around it, and Ageratum draws items natively
 * ({@code <item id="…"/>}), so the prose becomes real Markdown and the item stays a live item that
 * can be hovered for its tooltip and name.</p>
 *
 * <p>Patchouli's {@code item} field is not always a plain id. It may hold several stacks separated
 * by commas, an item tag ({@code #minecraft:logs}), or item components in brackets — Patchouli
 * writes those in SNBT while Ageratum's component form is JSON, so they are not translatable by
 * rewriting the text, and a page that has one declines. It is then hosted in Patchouli, which
 * draws it exactly.</p>
 */
public final class SpotlightPageRenderer implements PageRenderer {
    public static final String TYPE = "patchouli:spotlight";

    @Override
    public String render(PageRenderContext context) {
        List<String> items = items(context.raw("item"));
        if (items.isEmpty()) {
            return null;
        }

        String heading = context.title();
        StringBuilder section = new StringBuilder();
        if (!heading.isBlank()) {
            section.append("## ").append(heading).append("\n\n");
        }
        // A heading already names the page, and the item's own name would repeat it, so the item
        // shows its name only when the page gave none.
        section.append(itemBlock(items, heading.isBlank()));

        String text = context.renderText(context.text());
        if (!text.isBlank()) {
            section.append("\n\n").append(text);
        }
        return section.toString();
    }

    /**
     * The item ids a page asks for.
     *
     * @return the ids, or an empty list when the field is missing or holds anything this renderer
     *         cannot express faithfully — which makes the page fall back to being hosted
     */
    private static List<String> items(String field) {
        if (field == null || field.isBlank()) {
            return List.of();
        }
        List<String> items = new ArrayList<>();
        for (String part : field.split(",")) {
            String id = part.trim();
            if (id.indexOf('[') >= 0 || id.indexOf('{') >= 0 || id.startsWith("#")
                    || !Identifiers.isValid(id)) {
                return List.of();
            }
            items.add(id);
        }
        return items;
    }

    private static String itemBlock(List<String> items, boolean showName) {
        if (items.size() == 1) {
            return item(items.get(0), showName);
        }
        StringBuilder block = new StringBuilder("<row>\n");
        for (int index = 0; index < items.size(); index++) {
            if (index > 0) {
                block.append("\n---\n");
            }
            block.append('\n').append(item(items.get(index), true)).append('\n');
        }
        return block.append("</row>").toString();
    }

    private static String item(String id, boolean showName) {
        return showName
                ? "<item id=\"" + id + "\"/>"
                : "<item id=\"" + id + "\" showText=\"false\"/>";
    }
}
