package cn.xm1221.pta.core.book.recipe;

import cn.xm1221.pta.core.book.page.Identifiers;

/**
 * The two crafting recipes that swap a Patchouli book for its mirrored guide, and back, each with
 * the advancement that unlocks it in the recipe book.
 *
 * <p>A book and its guide are each one item plus a component saying which book the stack is:
 * Patchouli hands every book out as {@code patchouli:guide_book} carrying {@code patchouli:book},
 * and this mod's guides are the same shape. A recipe that named only the item would therefore
 * match every book in the pack, so both ingredients use NeoForge's {@code neoforge:components}
 * ingredient, which matches an item together with the components a stack has to carry.</p>
 *
 * <p>Nothing here reads a book. The caller supplies the book id and the guide's item and
 * component, so this stays part of the offline converter and is tested on its own; the files are
 * written one book at a time, which is what datagen would emit too if it could see books that only
 * exist in the mods a pack happens to be running.</p>
 *
 * <p>A method returns {@code null} when one of the ids could not be a resource location. That is
 * the caller's signal to leave the book without recipes rather than write one the game would
 * refuse to load.</p>
 */
public final class GuideRecipes {
    /** Patchouli's single book item, and the component that says which book a stack is. */
    public static final String PATCHOULI_ITEM = "patchouli:guide_book";
    public static final String PATCHOULI_BOOK = "patchouli:book";

    private GuideRecipes() {
    }

    /**
     * The recipe that turns the Patchouli book {@code book} into the guide for it.
     *
     * @param recipeNamespace the namespace the recipes belong to, i.e. the mod that owns the guide
     * @param guideItem      this mod's guide item
     * @param guideComponent the component that says which book a guide opens
     * @return the recipe and its unlock advancement, or {@code null} when an id is not a resource
     *         location
     */
    public static Conversion toGuide(String book, String recipeNamespace, String guideItem,
                                     String guideComponent) {
        return conversion(recipeNamespace, "to_guide", book, PATCHOULI_ITEM, PATCHOULI_BOOK,
                guideItem, guideComponent);
    }

    /**
     * The recipe that turns the guide for {@code book} back into its Patchouli book.
     *
     * @param recipeNamespace the namespace the recipes belong to, i.e. the mod that owns the guide
     * @param guideItem      this mod's guide item
     * @param guideComponent the component that says which book a guide opens
     * @return the recipe and its unlock advancement, or {@code null} when an id is not a resource
     *         location
     */
    public static Conversion toBook(String book, String recipeNamespace, String guideItem,
                                    String guideComponent) {
        return conversion(recipeNamespace, "to_book", book, guideItem, guideComponent,
                PATCHOULI_ITEM, PATCHOULI_BOOK);
    }

    /**
     * One direction of the conversion: the recipe, and the advancement that unlocks it once the
     * player has something to convert.
     *
     * @param path       the recipe's file path, e.g. {@code to_guide/hexcasting/thehexbook}
     * @param recipe     the recipe as JSON
     * @param unlockPath the file path of the advancement that unlocks it, under {@code recipes/}
     * @param unlock     the advancement as JSON
     */
    public record Conversion(String path, String recipe, String unlockPath, String unlock) {
    }

    private static Conversion conversion(String recipeNamespace, String prefix, String book,
                                         String fromItem, String fromComponent,
                                         String toItem, String toComponent) {
        if (!Identifiers.isValid(book) || !Identifiers.isValid(recipeNamespace + ":x")
                || !Identifiers.isValid(fromItem) || !Identifiers.isValid(fromComponent)
                || !Identifiers.isValid(toItem) || !Identifiers.isValid(toComponent)) {
            return null;
        }
        String path = prefix + "/" + book.replace(':', '/');
        String id = recipeNamespace + ":" + path;
        return new Conversion(path, recipe(fromItem, fromComponent, book, toItem, toComponent, book),
                "recipes/" + path, unlock(id, fromItem));
    }

    private static String recipe(String fromItem, String fromComponent, String fromBook,
                                 String toItem, String toComponent, String toBook) {
        return """
                {
                  "type": "minecraft:crafting_shapeless",
                  "category": "misc",
                  "ingredients": [
                    {
                      "type": "neoforge:components",
                      "items": "%s",
                      "components": {
                        "%s": "%s"
                      }
                    }
                  ],
                  "result": {
                    "id": "%s",
                    "components": {
                      "%s": "%s"
                    }
                  }
                }
                """.formatted(fromItem, fromComponent, fromBook, toItem, toComponent, toBook);
    }

    /**
     * The advancement that puts the recipe in the recipe book as soon as the player has the item
     * being converted. Without one the recipe still works in the grid, but never appears there.
     */
    private static String unlock(String recipe, String item) {
        return """
                {
                  "parent": "minecraft:recipes/root",
                  "criteria": {
                    "has_the_recipe": {
                      "conditions": {
                        "recipe": "%s"
                      },
                      "trigger": "minecraft:recipe_unlocked"
                    },
                    "has_item": {
                      "conditions": {
                        "items": [
                          {
                            "items": "%s"
                          }
                        ]
                      },
                      "trigger": "minecraft:inventory_changed"
                    }
                  },
                  "requirements": [
                    [
                      "has_the_recipe",
                      "has_item"
                    ]
                  ],
                  "rewards": {
                    "recipes": [
                      "%s"
                    ]
                  }
                }
                """.formatted(recipe, item, recipe);
    }
}
