package cn.xm1221.pta.core.book.page;

import cn.xm1221.pta.core.book.BookSource;
import cn.xm1221.pta.core.lang.Json5;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Renders Hex Casting's brainsweep pages as a centred recipe strip with Ageratum prose.
 *
 * <p>A brainsweep is not a vanilla recipe: it takes a block, a mob and some media, and gives back a
 * block, so Ageratum has no recipe factory for it and its {@code <recipe>} component cannot draw
 * it. Patchouli draws the page from a template of its own — a frame texture with the mob and the
 * stacks placed on it — which would otherwise be hosted, and a hosted page cannot be read,
 * searched or linked. So the page is rebuilt from the recipe's own datapack file: the mob, the
 * block that is swept, the media it costs and the block it yields go into one centred row, and the
 * prose becomes real Markdown.</p>
 *
 * <p>The row is what a reader needs, not a picture of Patchouli's frame, which is why the frame is
 * not reproduced. What the file says matters more than how Hex Casting paints it: the pieces are
 * live components, so each of them can be hovered for its own name.</p>
 *
 * <p>Anything the recipe file does not spell out plainly makes the page decline and be hosted
 * instead, which draws it exactly: a block or a mob given by tag, an ingredient kind this renderer
 * does not know, a recipe file that is missing or malformed, or a media cost that is not a whole
 * number of amethyst units.</p>
 */
public final class BrainsweepPageRenderer implements PageRenderer {
    public static final String TYPE = "hexcasting:brainsweep";

    private static final String VILLAGER = "minecraft:villager";
    private static final String DEFAULT_PROFESSION = "minecraft:toolsmith";
    private static final String DEFAULT_BIOME = "minecraft:plains";

    /**
     * Media per amethyst unit, as Hex Casting defines them
     * ({@code at.petrak.hexcasting.api.misc.MediaConstants}): a dust is 10 000, a shard five dust,
     * a charged amethyst ten dust.
     */
    private static final long DUST = 10_000L;

    private static final List<Unit> UNITS = List.of(
            new Unit(10 * DUST, "hexcasting:charged_amethyst"),
            new Unit(5 * DUST, "minecraft:amethyst_shard"),
            new Unit(DUST, "hexcasting:amethyst_dust"));

    @Override
    public String render(PageRenderContext context) {
        Map<?, ?> recipe = recipe(context);
        if (recipe == null) {
            return null;
        }

        List<String> parts = new ArrayList<>(4);
        parts.add(mob(recipe.get("entityIn")));
        parts.add(block(recipe.get("blockIn")));
        parts.add(media(recipe.get("cost")));
        parts.add(result(recipe.get("result")));
        if (parts.contains(null)) {
            return null;
        }

        StringBuilder section = new StringBuilder(row(parts));
        String text = context.renderText(context.text());
        if (!text.isBlank()) {
            section.append("\n\n").append(text);
        }
        return section.toString();
    }

    /**
     * The recipe a page names.
     *
     * <p>The page carries an id, not the recipe: a brainsweep page says
     * {@code hexcasting:brainsweep/budding_amethyst} and the recipe is a file of that name in the
     * {@code data/} of the mod that owns it. Both the current {@code recipe} directory and the
     * pre-1.21 {@code recipes} one are read, because a book can be older than the game.</p>
     *
     * @return the recipe, or {@code null} when the page names none or it cannot be read
     */
    private static Map<?, ?> recipe(PageRenderContext context) {
        String id = string(context.raw("recipe"));
        if (!Identifiers.isValid(id)) {
            return null;
        }
        int colon = id.indexOf(':');
        String path = "data/" + id.substring(0, colon) + "/";
        BookSource source = context.layout().source();
        for (String directory : List.of("recipe/", "recipes/")) {
            String text = source.read(path + directory + id.substring(colon + 1) + ".json");
            if (text == null) {
                continue;
            }
            try {
                return Json5.parse(text) instanceof Map<?, ?> recipe ? recipe : null;
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }
        return null;
    }

    /**
     * The mob that is swept, as an entity component.
     *
     * <p>A villager is drawn the way Patchouli draws it: the profession the recipe asks for, plains
     * when it asks for no particular kind, and level 1 — Hex Casting caps its own display at level
     * 1 whatever the recipe's minimum level is, so the picture never states the requirement.</p>
     *
     * @return the component, or {@code null} for a mob given by tag or by an unknown ingredient
     */
    private static String mob(Object value) {
        Map<?, ?> json = map(value);
        if (json == null) {
            return null;
        }
        String type = string(json.get("type"));
        if (type == null) {
            return null;
        }
        return switch (type) {
            case "hexcasting:villager" -> entity(VILLAGER, villagerData(json));
            case "hexcasting:entity_type" -> entity(string(json.get("entityType")), null);
            default -> null;
        };
    }

    /** The villager's data, written as the SNBT Patchouli's own example entity carries. */
    private static String villagerData(Map<?, ?> json) {
        String profession = string(json.get("profession"));
        String biome = string(json.get("biome"));
        return "{VillagerData:{profession:\""
                + (profession == null ? DEFAULT_PROFESSION : profession)
                + "\",type:\"" + (biome == null ? DEFAULT_BIOME : biome)
                + "\",level:1}}";
    }

    /**
     * The block that is swept.
     *
     * <p>It is drawn as a block rather than as an item: the ingredient is a block by definition, and
     * a block that ships without an item — Hex Casting's directrices, for one — would show as
     * nothing at all.</p>
     */
    private static String block(Object value) {
        Map<?, ?> json = map(value);
        if (json == null || !"hexcasting:block".equals(string(json.get("type")))) {
            return null;
        }
        return blockComponent(string(json.get("block")));
    }

    /** The block a sweep yields, which the recipe writes as a block state. */
    private static String result(Object value) {
        Map<?, ?> json = map(value);
        if (json == null) {
            return null;
        }
        return blockComponent(string(json.get("Name")));
    }

    /**
     * The media a sweep costs, as the largest amethyst unit that divides it exactly.
     *
     * @return the component, or {@code null} when the cost is not a whole number of units, which is
     *         not something this renderer can state without rounding the number up
     */
    private static String media(Object value) {
        if (!(value instanceof Number number)) {
            return null;
        }
        long cost = number.longValue();
        for (Unit unit : UNITS) {
            if (cost > 0 && cost % unit.amount() == 0) {
                return "<item id=\"" + unit.item() + "\" count=\"" + (cost / unit.amount())
                        + "\" showText=\"false\"/>";
            }
        }
        return null;
    }

    /**
     * The recipe's parts side by side, centred.
     *
     * <p>Ageratum lays a row out itself, so the parts are simply its children — separated by a
     * horizontal rule line, the row's own child separator. None of them shows its name: four names
     * beside four pictures is more than a page line holds, and each picture can be hovered.</p>
     */
    private static String row(List<String> parts) {
        StringBuilder row = new StringBuilder("<row halign=\"center\" valign=\"center\">\n");
        for (int index = 0; index < parts.size(); index++) {
            if (index > 0) {
                row.append("\n---\n");
            }
            row.append('\n').append(parts.get(index)).append('\n');
        }
        return row.append("</row>").toString();
    }

    private static String entity(String id, String nbt) {
        if (!Identifiers.isValid(id)) {
            return null;
        }
        StringBuilder tag = new StringBuilder("<entity");
        attribute(tag, "id", id);
        if (nbt != null) {
            // Entity data is SNBT and holds double quotes, so the value is quoted with single ones,
            // which is the other spelling Ageratum's tag parser accepts.
            tag.append(" nbt='").append(nbt).append('\'');
        }
        return tag.append(" showText=\"false\"/>").toString();
    }

    private static String blockComponent(String id) {
        return Identifiers.isValid(id) ? "<block id=\"" + id + "\" showText=\"false\"/>" : null;
    }

    private static void attribute(StringBuilder tag, String name, String value) {
        tag.append(' ').append(name).append("=\"").append(value).append('"');
    }

    private static Map<?, ?> map(Object value) {
        return value instanceof Map<?, ?> json ? json : null;
    }

    private static String string(Object value) {
        return value instanceof String text && !text.isBlank() ? text.trim() : null;
    }

    /** One amethyst unit: how much media it holds and the item that carries it. */
    private record Unit(long amount, String item) {
    }
}
