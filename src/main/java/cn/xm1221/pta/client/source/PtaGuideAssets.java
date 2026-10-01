package cn.xm1221.pta.client.source;

import cn.xm1221.pta.PtaBookList;
import cn.xm1221.pta.PtaGuides;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The item models that dress a guidebook as the manual it opens.
 *
 * <p>Patchouli books are not all the same object: each declares the model and texture it wears, and
 * Hex Casting's is a blue book of its own. Reusing those declarations costs nothing and keeps this
 * mod from inventing an appearance, so the base model inherits the first mirrored book's model
 * outright, and every further book gets a model of its own selected by
 * {@code custom_model_data} — the same index the creative tab sets on its stacks.</p>
 *
 * <p>These files are served from the generated pack, so they are rebuilt with it whenever the
 * configuration changes, and they cannot drift from the books actually being mirrored.</p>
 */
public final class PtaGuideAssets {
    /** What a book with no model of its own wears, matching Patchouli's own default. */
    private static final String FALLBACK_MODEL = "patchouli:item/book_brown";

    private static final String ITEM_PATH = "models/item/";

    private PtaGuideAssets() {
    }

    /** Builds every generated item model, keyed by the resource location it is served at. */
    public static Map<ResourceLocation, String> build() {
        Map<ResourceLocation, String> assets = new LinkedHashMap<>();

        List<String> models = new ArrayList<>();
        for (ResourceLocation book : PtaBookList.effectiveBooks()) {
            models.add(PtaGuides.bookItemModel(book));
        }
        if (models.isEmpty()) {
            models.add(FALLBACK_MODEL);
        }

        assets.put(itemModel("guidebook"), baseModel(models));
        for (int index = 1; index < models.size(); index++) {
            assets.put(itemModel("guidebook_" + index), inherit(models.get(index)));
        }
        return assets;
    }

    /** The model every guidebook wears, with one override per further book. */
    private static String baseModel(List<String> models) {
        StringBuilder json = new StringBuilder("{\n  \"parent\": \"").append(models.get(0)).append('"');
        if (models.size() > 1) {
            json.append(",\n  \"overrides\": [");
            for (int index = 1; index < models.size(); index++) {
                json.append(index > 1 ? "," : "")
                        .append("\n    {\"predicate\": {\"custom_model_data\": ").append(index)
                        .append("}, \"model\": \"pta:item/guidebook_").append(index).append("\"}");
            }
            json.append("\n  ]");
        }
        return json.append("\n}\n").toString();
    }

    /** A model that is nothing but the book's own model, rewritten to point at ours. */
    private static String inherit(String model) {
        return "{\n  \"parent\": \"" + model + "\"\n}\n";
    }

    private static ResourceLocation itemModel(String name) {
        return ResourceLocation.fromNamespaceAndPath("pta", ITEM_PATH + name + ".json");
    }
}
