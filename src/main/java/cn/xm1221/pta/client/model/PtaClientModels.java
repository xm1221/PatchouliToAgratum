package cn.xm1221.pta.client.model;

import cn.xm1221.pta.PtaBookList;
import cn.xm1221.pta.PtaGuides;
import cn.xm1221.pta.PtaMod;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ModelEvent;

import java.util.Map;

/**
 * Bakes the books' models so a guidebook can wear them.
 *
 * <p>A model is normally baked because an item or a blockstate points at it. The books' models are
 * pointed at by nothing: Patchouli names them from a data file and draws them itself. So they are
 * asked for explicitly, and then the guidebook's own model is replaced with one that knows how to
 * pick between them per stack (see {@link GuideBookModel}).</p>
 */
public final class PtaClientModels {
    private PtaClientModels() {
    }

    /** Asks the bakery for every mirrored book's model, plus this mod's own. */
    public static void onRegisterAdditional(ModelEvent.RegisterAdditional event) {
        for (ResourceLocation book : PtaBookList.effectiveBooks()) {
            event.register(ModelResourceLocation.standalone(PtaGuides.bookItemModel(book)));
        }
    }

    /**
     * Replaces the guidebook's baked model with the one that resolves per stack.
     *
     * <p>The resolver reads the baking result rather than the model manager, so it cannot be called
     * before the models exist and cannot see a half-built one afterwards.</p>
     */
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        ModelResourceLocation guidebook = ModelResourceLocation.inventory(
                ResourceLocation.fromNamespaceAndPath(PtaMod.MOD_ID, "guidebook"));
        BakedModel unbound = models.get(guidebook);
        if (unbound == null) {
            return;
        }
        models.put(guidebook, new GuideBookModel(unbound,
                model -> models.get(ModelResourceLocation.standalone(model))));
    }
}
