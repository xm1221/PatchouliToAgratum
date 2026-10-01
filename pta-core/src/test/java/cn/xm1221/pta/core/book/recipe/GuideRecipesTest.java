package cn.xm1221.pta.core.book.recipe;

import cn.xm1221.pta.core.lang.Json5;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A recipe is how a player gets a guide without the creative inventory, so these pin what decides
 * whether the pair works at all: the ingredient names the book through a component, because both
 * sides are one shared item, the result carries the component that the item on the other side
 * reads, and the advancement unlocks the recipe with the full recipe id.
 */
class GuideRecipesTest {
    private static final String BOOK = "hexcasting:thehexbook";
    private static final String NAMESPACE = "pta";
    private static final String GUIDE_ITEM = "pta:guidebook";
    private static final String GUIDE_COMPONENT = "pta:guide";

    private static GuideRecipes.Conversion toGuide() {
        return GuideRecipes.toGuide(BOOK, NAMESPACE, GUIDE_ITEM, GUIDE_COMPONENT);
    }

    private static GuideRecipes.Conversion toBook() {
        return GuideRecipes.toBook(BOOK, NAMESPACE, GUIDE_ITEM, GUIDE_COMPONENT);
    }

    @Test
    void thePatchouliBookBecomesTheGuideForThatBook() {
        String json = toGuide().recipe();

        assertTrue(json.contains("\"type\": \"minecraft:crafting_shapeless\""), json);
        assertTrue(json.contains("\"type\": \"neoforge:components\""), json);
        assertTrue(json.contains("\"items\": \"patchouli:guide_book\""), json);
        assertTrue(json.contains("\"patchouli:book\": \"" + BOOK + "\""), json);
        assertTrue(json.contains("\"id\": \"" + GUIDE_ITEM + "\""), json);
        assertTrue(json.contains("\"" + GUIDE_COMPONENT + "\": \"" + BOOK + "\""), json);
    }

    @Test
    void theGuideBecomesThePatchouliBookItCameFrom() {
        String json = toBook().recipe();

        assertTrue(json.contains("\"items\": \"" + GUIDE_ITEM + "\""), json);
        assertTrue(json.contains("\"" + GUIDE_COMPONENT + "\": \"" + BOOK + "\""), json);
        assertTrue(json.contains("\"id\": \"patchouli:guide_book\""), json);
        assertTrue(json.contains("\"patchouli:book\": \"" + BOOK + "\""), json);
    }

    @Test
    @SuppressWarnings("unchecked")
    void theRecipeIsAShapelessCraftWithOneIngredientAndOneResult() {
        Map<String, Object> recipe = (Map<String, Object>) Json5.parse(toGuide().recipe());

        assertEquals("minecraft:crafting_shapeless", recipe.get("type"));
        List<Object> ingredients = (List<Object>) recipe.get("ingredients");
        assertEquals(1, ingredients.size());
        Map<String, Object> result = (Map<String, Object>) recipe.get("result");
        assertEquals(GUIDE_ITEM, result.get("id"));
    }

    @Test
    void eachDirectionGetsItsOwnRecipeAndAdvancement() {
        assertEquals("to_guide/hexcasting/thehexbook", toGuide().path());
        assertEquals("recipes/to_guide/hexcasting/thehexbook", toGuide().unlockPath());
        assertEquals("to_book/hexcasting/thehexbook", toBook().path());
        assertEquals("recipes/to_book/hexcasting/thehexbook", toBook().unlockPath());
    }

    @Test
    @SuppressWarnings("unchecked")
    void theAdvancementUnlocksTheRecipeAndWaitsForTheItemBeingConverted() {
        Map<String, Object> unlock = (Map<String, Object>) Json5.parse(toGuide().unlock());
        String recipe = NAMESPACE + ":to_guide/hexcasting/thehexbook";

        assertEquals("minecraft:recipes/root", unlock.get("parent"));
        assertEquals(List.of(recipe), ((Map<String, Object>) unlock.get("rewards")).get("recipes"));
        Map<String, Object> criteria = (Map<String, Object>) unlock.get("criteria");
        assertEquals(recipe, ((Map<String, Object>) ((Map<String, Object>) criteria.get("has_the_recipe"))
                .get("conditions")).get("recipe"));
        assertEquals("minecraft:inventory_changed",
                ((Map<String, Object>) criteria.get("has_item")).get("trigger"));
    }

    @Test
    void theReverseRecipeIsUnlockedByTheGuideItself() {
        assertTrue(toBook().unlock().contains("\"items\": \"" + GUIDE_ITEM + "\""), toBook().unlock());
    }

    @Test
    void anIdThatCannotBeAResourceLocationIsDeclined() {
        assertNull(GuideRecipes.toGuide("Hexcasting:TheBook", NAMESPACE, GUIDE_ITEM, GUIDE_COMPONENT));
        assertNull(GuideRecipes.toBook(BOOK, NAMESPACE, GUIDE_ITEM, "not an id"));
        assertNull(GuideRecipes.toGuide(BOOK, "NotANamespace", GUIDE_ITEM, GUIDE_COMPONENT));
        assertNotNull(toGuide());
    }
}
