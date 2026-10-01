package cn.xm1221.pta.client.model;

import cn.xm1221.pta.PtaGuides;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

/**
 * The guidebook's model, which wears whichever manual the stack names.
 *
 * <p>A stack's model is otherwise decided before the stack exists: an item gets one model, and
 * anything else it might look like is a {@code custom_model_data} override chosen by whoever built
 * the stack. Neither works here, because the book is a data component that anything can set — a
 * {@code /give} with a component, a book from another mod, a stack built in code. So the model is
 * chosen while the stack is drawn, through the one hook the game offers for it: the item's
 * {@link ItemOverrides} are consulted per stack, and this one reads the component and answers with
 * that book's model.</p>
 *
 * <p>This is Patchouli's own approach to the same problem — its {@code BookModel} is a
 * {@link BakedModel} whose overrides resolve per stack — and it costs nothing at draw time beyond
 * one map lookup, because the book models are baked up front (see {@code PtaClientModels}).</p>
 */
public class GuideBookModel implements BakedModel {
    /** What a guidebook with no book in it wears: this mod's own model, on Ageratum's texture. */
    private final BakedModel unbound;
    private final ItemOverrides overrides;

    /**
     * @param unbound the model the item was given, worn when the stack names no book
     * @param models  a book's model id to the baked model, or {@code null} when it was never baked
     */
    public GuideBookModel(BakedModel unbound, Function<ResourceLocation, BakedModel> models) {
        this.unbound = unbound;
        this.overrides = new BookOverrides(models);
    }

    @Override
    public ItemOverrides getOverrides() {
        return this.overrides;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction direction,
                                    RandomSource random) {
        return this.unbound.getQuads(state, direction, random);
    }

    @Override
    public boolean useAmbientOcclusion() {
        return this.unbound.useAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return this.unbound.isGui3d();
    }

    @Override
    public boolean usesBlockLight() {
        return this.unbound.usesBlockLight();
    }

    @Override
    public boolean isCustomRenderer() {
        return this.unbound.isCustomRenderer();
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return this.unbound.getParticleIcon();
    }

    /** The per-stack half: which book, therefore which model. */
    private static final class BookOverrides extends ItemOverrides {
        private final Function<ResourceLocation, BakedModel> models;

        private BookOverrides(Function<ResourceLocation, BakedModel> models) {
            this.models = models;
        }

        @Override
        public BakedModel resolve(BakedModel base, ItemStack stack, @Nullable ClientLevel level,
                                  @Nullable LivingEntity entity, int seed) {
            ResourceLocation book = PtaGuides.bookOf(stack);
            if (book == null) {
                return base;
            }
            // A book whose model never got baked falls back to the unbound book rather than
            // drawing nothing.
            BakedModel model = this.models.apply(PtaGuides.bookItemModel(book));
            return model == null ? base : model;
        }
    }
}
