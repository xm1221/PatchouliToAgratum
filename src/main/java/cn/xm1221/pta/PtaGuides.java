package cn.xm1221.pta;

import cn.xm1221.pta.client.source.ModFileBookSource;
import cn.xm1221.pta.core.lang.Json5;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import dev.anvilcraft.resource.ageratum.Ageratum;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where a mirrored book lives once it is a guide.
 *
 * <p>A mirrored book keeps its namespace: Hex Casting's book becomes the guide in namespace
 * {@code hexcasting}, whose documents sit beside the ones the mod itself ships. Opening it is
 * therefore just {@code <namespace>:index}, the same entry point {@code /ageratum <namespace>}
 * uses, and this class is the one place that knows it.</p>
 *
 * <p>A book's own {@code book.json} is what says what it is called and what it wears. That file is
 * read once per book per session: the item asks for both every time it is drawn, and re-reading a
 * file from a mod jar to draw an item would be absurd.</p>
 */
public final class PtaGuides {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The document every Ageratum guide is entered through. */
    private static final String ENTRY = "index";

    /** What a book that names no model of its own wears, matching Patchouli's own default. */
    private static final ResourceLocation DEFAULT_MODEL =
            ResourceLocation.fromNamespaceAndPath("patchouli", "item/book_brown");

    private static final Map<ResourceLocation, Optional<String>> NAMES = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, ResourceLocation> MODELS = new ConcurrentHashMap<>();

    private PtaGuides() {
    }

    /**
     * The book a guidebook stack opens.
     *
     * @return the book's id, or {@code null} when the stack names none
     */
    public static @Nullable ResourceLocation bookOf(ItemStack stack) {
        return stack.get(PtaDataComponents.GUIDE.get());
    }

    /**
     * Opens a mirrored book as a guide.
     *
     * @param book the book to open
     * @return whether anything was opened
     */
    public static boolean open(ServerPlayer player, ResourceLocation book) {
        // The guide is entered by document, not by book: a book's documents live in the book's own
        // namespace, so the entry point is <namespace>:index.
        Ageratum.openGuide(player, ResourceLocation.fromNamespaceAndPath(book.getNamespace(), ENTRY));
        return true;
    }

    /**
     * A book's {@code book.json}, which holds both its display name and the model to wear.
     *
     * @return the file's text, or {@code null} when the book has no readable {@code book.json}
     */
    public static @Nullable String bookJson(ResourceLocation book) {
        return new ModFileBookSource().read(
                "data/" + book.getNamespace() + "/patchouli_books/" + book.getPath() + "/book.json");
    }

    /**
     * The language key a book names itself with.
     *
     * <p>Patchouli stores the name as a key in the book's own language file, which the client
     * already has loaded, so the guidebook can show the book's real name in the player's own
     * language without this mod shipping a translation for every book it mirrors.</p>
     *
     * @return the key as written in {@code book.json}, or {@code null} when there is none
     */
    public static @Nullable String bookNameKey(ResourceLocation book) {
        return NAMES.computeIfAbsent(book, id -> Optional.ofNullable(bookString(id, "name")))
                .orElse(null);
    }

    /**
     * The model a book wears, as the item model that actually exists on disk.
     *
     * <p>Patchouli's {@code model} field names a book model without the {@code item/} folder it
     * lives in — Hex Casting declares {@code hexcasting:patchouli_book} and ships
     * {@code assets/hexcasting/models/item/patchouli_book.json} — so the folder is added here.</p>
     *
     * @return the item model id, or the default brown Patchouli book when the book names none
     */
    public static ResourceLocation bookItemModel(ResourceLocation book) {
        return MODELS.computeIfAbsent(book, PtaGuides::readBookModel);
    }

    private static ResourceLocation readBookModel(ResourceLocation book) {
        String declared = bookString(book, "model");
        ResourceLocation model = declared == null ? null : ResourceLocation.tryParse(declared);
        if (model == null) {
            return DEFAULT_MODEL;
        }
        return model.getPath().startsWith("item/")
                ? model
                : ResourceLocation.fromNamespaceAndPath(model.getNamespace(), "item/" + model.getPath());
    }

    /**
     * One string field of a book's {@code book.json}.
     *
     * @return the value, or {@code null} when the file or the field is missing
     */
    private static @Nullable String bookString(ResourceLocation book, String field) {
        String json = bookJson(book);
        if (json == null) {
            return null;
        }
        try {
            Object parsed = Json5.parse(json);
            if (parsed instanceof Map<?, ?> map && map.get(field) instanceof String value && !value.isBlank()) {
                return value.trim();
            }
        } catch (RuntimeException exception) {
            LOGGER.debug("[pta] could not read {} of {}", field, book, exception);
        }
        return null;
    }
}
