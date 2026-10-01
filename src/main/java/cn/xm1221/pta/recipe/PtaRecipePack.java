package cn.xm1221.pta.recipe;

import cn.xm1221.pta.PtaBookList;
import cn.xm1221.pta.PtaDataComponents;
import cn.xm1221.pta.PtaItems;
import cn.xm1221.pta.PtaMod;
import cn.xm1221.pta.core.book.recipe.GuideRecipes;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.world.flag.FeatureFlags;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * The book-to-guide crafting recipes, served as a data pack.
 *
 * <p>The recipes are written here rather than shipped as files because they are one pair per book,
 * and the books are whatever the running pack has installed; a datagen run cannot see a book that
 * only exists in another mod's jar. Serving them is what makes them real recipes: the server reads
 * this pack like any other data pack, and hands them to the client with the rest.</p>
 *
 * <p>Unlike the guides this is server data, so it is registered for {@link PackType#SERVER_DATA}
 * and from the common entry point — a dedicated server has to hand these recipes out even though
 * it never mirrors a document. Nothing here touches Patchouli: a book's item is Patchouli's one
 * shared book item plus a component naming the book, which is exactly what the recipes use.</p>
 */
public final class PtaRecipePack implements PackResources {
    public static final String PACK_ID = "pta_conversion_recipes";

    public static final PackLocationInfo LOCATION = new PackLocationInfo(
            PACK_ID,
            Component.literal("Patchouli to Ageratum recipes"),
            PackSource.BUILT_IN,
            Optional.empty()
    );

    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<ResourceLocation, String> recipes;

    public PtaRecipePack() {
        Map<ResourceLocation, String> generated;
        try {
            generated = new LinkedHashMap<>(build());
        } catch (Throwable throwable) {
            // This runs while the game is assembling its data packs, where an exception would take
            // the whole reload down. No recipes is the correct response to being unable to write
            // them; the guides themselves are unaffected.
            LOGGER.error("[pta] could not write the book conversion recipes", throwable);
            generated = Map.of();
        }
        this.recipes = generated;
    }

    /**
     * Handles {@link AddPackFindersEvent} on the mod event bus.
     *
     * @param event the pack discovery event, fired once per pack type during startup
     */
    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) {
            return;
        }
        event.addRepositorySource(consumer -> consumer.accept(new Pack(
                LOCATION,
                new Pack.ResourcesSupplier() {
                    @Override
                    public PackResources openPrimary(PackLocationInfo info) {
                        return new PtaRecipePack();
                    }

                    @Override
                    public PackResources openFull(PackLocationInfo info, Pack.Metadata metadata) {
                        return new PtaRecipePack();
                    }
                },
                new Pack.Metadata(
                        Component.literal("Craft a Patchouli book into its mirrored guide, and back"),
                        PackCompatibility.COMPATIBLE,
                        FeatureFlags.VANILLA_SET,
                        List.of(),
                        // Hidden: it is always active and cannot be moved, so listing it on the
                        // data pack screen would only be noise.
                        true
                ),
                new PackSelectionConfig(true, Pack.Position.BOTTOM, false)
        )));
    }

    /** How many recipes this pack is serving, for diagnostics. */
    public int recipeCount() {
        return this.recipes.size();
    }

    /**
     * Writes one pair of recipes for every mirrored book.
     *
     * <p>Patchouli's item is checked for first: it is a client-optional mod, so a dedicated server
     * may be running without it, and a recipe naming an item that does not exist is an error in
     * the log on every start. Without it there is nothing to convert from, so nothing is written.
     * </p>
     */
    private static Map<ResourceLocation, String> build() {
        Map<ResourceLocation, String> files = new LinkedHashMap<>();
        if (!BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(GuideRecipes.PATCHOULI_ITEM))) {
            LOGGER.info("[pta] not writing book conversion recipes: {} is not installed",
                    GuideRecipes.PATCHOULI_ITEM);
            return files;
        }

        String guideItem = PtaItems.GUIDEBOOK.getId().toString();
        String guideComponent = PtaDataComponents.GUIDE.getId().toString();
        List<ResourceLocation> books = PtaBookList.effectiveBooks();
        for (ResourceLocation book : books) {
            String id = book.toString();
            add(files, GuideRecipes.toGuide(id, PtaMod.MOD_ID, guideItem, guideComponent), id);
            add(files, GuideRecipes.toBook(id, PtaMod.MOD_ID, guideItem, guideComponent), id);
        }
        LOGGER.debug("[pta] conversion recipes for {} books: {} files", books.size(), files.size());
        return files;
    }

    /**
     * Writes one conversion: its recipe, and the advancement that puts it in the recipe book.
     *
     * @param conversion the two files, or {@code null} when the book's ids could not be written
     * @param book       the book, for the log line that says what was skipped
     */
    private static void add(Map<ResourceLocation, String> files, @Nullable GuideRecipes.Conversion conversion,
                            String book) {
        if (conversion == null) {
            LOGGER.warn("[pta] not writing a conversion recipe for {}: its id cannot be a resource location", book);
            return;
        }
        files.put(recipeLocation("recipe/" + conversion.path() + ".json"), conversion.recipe());
        files.put(recipeLocation("advancement/" + conversion.unlockPath() + ".json"), conversion.unlock());
    }

    private static ResourceLocation recipeLocation(String path) {
        return ResourceLocation.fromNamespaceAndPath(PtaMod.MOD_ID, path);
    }

    @Override
    public @Nullable IoSupplier<InputStream> getRootResource(String... elements) {
        return null;
    }

    @Override
    public @Nullable IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        if (type != PackType.SERVER_DATA) {
            return null;
        }
        String text = this.recipes.get(location);
        return text == null ? null : () -> new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        if (type != PackType.SERVER_DATA) {
            return;
        }
        String prefix = path.endsWith("/") ? path : path + "/";
        for (Map.Entry<ResourceLocation, String> recipe : this.recipes.entrySet()) {
            ResourceLocation location = recipe.getKey();
            if (!location.getNamespace().equals(namespace) || !location.getPath().startsWith(prefix)) {
                continue;
            }
            String text = recipe.getValue();
            output.accept(location, () -> new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        if (type != PackType.SERVER_DATA) {
            return Set.of();
        }
        Set<String> namespaces = new TreeSet<>();
        for (ResourceLocation location : this.recipes.keySet()) {
            namespaces.add(location.getNamespace());
        }
        return namespaces;
    }

    @Override
    public <T> @Nullable T getMetadataSection(MetadataSectionSerializer<T> serializer) {
        return null;
    }

    @Override
    public PackLocationInfo location() {
        return LOCATION;
    }

    @Override
    public void close() {
        // Nothing to release: the recipes are plain strings.
    }

    /** A map view for tests and diagnostics. */
    public Map<ResourceLocation, String> recipes() {
        return new LinkedHashMap<>(this.recipes);
    }
}
