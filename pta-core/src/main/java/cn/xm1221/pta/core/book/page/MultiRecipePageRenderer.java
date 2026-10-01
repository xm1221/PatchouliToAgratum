package cn.xm1221.pta.core.book.page;

import java.util.List;

/**
 * Renders Hex Casting's {@code crafting_multi} page, which lists every variant of one recipe.
 *
 * <p>The page is a single crafting grid whose ingredient slots cycle through all the variants —
 * Hex Casting merges their ingredients to say "any of these dyes". Ageratum has no merged-slot
 * component, so the variants are expanded into one native recipe each: less compact than the
 * original, but every variant is exact and hoverable, which is the better trade in a guide that is
 * meant to be read and searched. The difference is recorded in the conversion report.</p>
 *
 * <p>Deliberately Hex Casting specific: it is registered against that page type by id, and a mod
 * that ships no such page is unaffected. Nothing here depends on Hex Casting being installed.</p>
 */
public final class MultiRecipePageRenderer implements PageRenderer {
    /** The page type this renderer answers for. */
    public static final String TYPE = "hexcasting:crafting_multi";

    private final RecipePageRenderer layouts = new RecipePageRenderer();

    @Override
    public String render(PageRenderContext context) {
        List<String> recipes = context.rawList("recipes");
        if (recipes.isEmpty()) {
            return null;
        }
        context.report().multiRecipe(context.type() + "@" + context.documentPath());

        // The heading is Hex Casting's own field; fall back to the generic title if a book uses one.
        String heading = context.localised("heading");
        if (heading.isBlank()) {
            heading = layouts.heading(context);
        }
        return layouts.section(context, heading, recipes);
    }
}
