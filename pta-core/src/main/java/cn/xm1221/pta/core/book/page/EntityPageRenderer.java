package cn.xm1221.pta.core.book.page;

/**
 * Renders Patchouli's entity pages as Ageratum's entity component.
 *
 * <p>An entity page shows one entity with its name, and both mods take the entity by id, so the
 * page becomes {@code <entity id="…"/>} plus its prose.</p>
 *
 * <p>A page whose entity carries NBT — {@code minecraft:creeper{powered:1}} — declines. Patchouli
 * writes that data inside the id in SNBT, while Ageratum takes it as a separate parameter, and
 * whether it expects the same SNBT there is not something this converter can know. The page is
 * hosted instead, which draws the powered creeper exactly.</p>
 */
public final class EntityPageRenderer implements PageRenderer {
    public static final String TYPE = "patchouli:entity";

    @Override
    public String render(PageRenderContext context) {
        String entity = context.raw("entity");
        if (entity == null || entity.isBlank()) {
            return null;
        }
        String id = entity.trim();
        if (id.indexOf('{') >= 0 || id.indexOf('[') >= 0 || !Identifiers.isValid(id)) {
            return null;
        }

        String heading = context.title();
        StringBuilder section = new StringBuilder();
        if (!heading.isBlank()) {
            section.append("## ").append(heading).append("\n\n");
        }
        section.append("<entity id=\"").append(id).append("\"/>");

        String text = context.renderText(context.text());
        if (!text.isBlank()) {
            section.append("\n\n").append(text);
        }
        return section.toString();
    }
}
