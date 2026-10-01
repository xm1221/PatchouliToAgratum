package cn.xm1221.pta.core.book.page;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders Patchouli's recipe pages as Ageratum's native recipe component.
 *
 * <p>Patchouli keeps a recipe page down to a recipe id, and Ageratum can draw a recipe from an id
 * ({@code <recipe id="…"/>}), so the two line up exactly: the recipe is drawn by the guide's own
 * renderer — live, hoverable, reflecting the installed recipes — and the page's prose becomes real
 * Markdown underneath it.</p>
 *
 * <p>Applies to every recipe page type Patchouli ships. Ageratum resolves the id against the
 * recipe manager and picks its own component for whatever type the recipe turns out to be, so this
 * renderer never has to know whether a page held a crafting, smelting or smithing recipe. Two
 * caveats worth knowing (also recorded in the project plan): Ageratum draws every cooking recipe on
 * a furnace background, and a recipe whose type Ageratum has no component for would be drawn as
 * nothing, which is why a page without a usable recipe id declines instead of guessing.</p>
 */
public class RecipePageRenderer implements PageRenderer {
    /**
     * The page types this renderer answers for.
     *
     * <p>{@code patchouli:campfire} is Patchouli's own id for campfire cooking; the class behind it
     * is {@code PageCampfireCooking}.</p>
     */
    public static final List<String> TYPES = List.of(
            "patchouli:crafting",
            "patchouli:smelting",
            "patchouli:blasting",
            "patchouli:smoking",
            "patchouli:campfire",
            "patchouli:smithing",
            "patchouli:stonecutting");

    @Override
    public String render(PageRenderContext context) {
        List<String> recipes = recipeIds(context);
        if (recipes.isEmpty()) {
            // No id means nothing native to draw. Patchouli might still find something, so hand the
            // page back rather than emit an empty recipe.
            return null;
        }
        return section(context, heading(context), recipes);
    }

    /**
     * The heading a recipe page shows above its recipes.
     *
     * @return Patchouli's optional page title, empty when the page has none
     */
    protected String heading(PageRenderContext context) {
        return context.title();
    }

    /**
     * Builds one converted section: heading, recipes, prose.
     *
     * <p>Shared with the renderers that collect their recipe ids differently, so that a page with
     * one recipe and a page with fifteen read the same way.</p>
     *
     * @param heading the resolved heading, empty for none
     * @param recipes the recipe ids to draw, never empty
     */
    protected final String section(PageRenderContext context, String heading, List<String> recipes) {
        StringBuilder section = new StringBuilder();
        if (!heading.isBlank()) {
            section.append("## ").append(heading).append("\n\n");
        }
        section.append(recipeBlock(recipes));

        String text = context.renderText(context.text());
        if (!text.isBlank()) {
            section.append("\n\n").append(text);
        }
        return section.toString();
    }

    /**
     * The recipe ids a page references: {@code recipe} and the optional second one Patchouli's
     * double-recipe pages carry.
     */
    protected List<String> recipeIds(PageRenderContext context) {
        List<String> recipes = new ArrayList<>(2);
        addRecipe(recipes, context.raw("recipe"));
        addRecipe(recipes, context.raw("recipe2"));
        return recipes;
    }

    private static void addRecipe(List<String> recipes, String id) {
        if (id != null && !id.isBlank()) {
            recipes.add(id.trim());
        }
    }

    /**
     * Lays the recipes out: a single one on its own, several side by side in a row.
     *
     * <p>A row wraps when it runs out of width, which is what keeps a page with a dozen recipe
     * variants readable.</p>
     */
    private static String recipeBlock(List<String> recipes) {
        if (recipes.size() == 1) {
            return recipe(recipes.get(0));
        }
        StringBuilder out = new StringBuilder();
        out.append("<row>\n");
        for (int index = 0; index < recipes.size(); index++) {
            if (index > 0) {
                out.append("\n---\n");
            }
            out.append('\n').append(recipe(recipes.get(index))).append('\n');
        }
        out.append("</row>");
        return out.toString();
    }

    private static String recipe(String id) {
        return "<recipe id=\"" + id + "\"/>";
    }
}
