package cn.xm1221.pta.client.source;

import cn.xm1221.pta.PtaBookList;
import cn.xm1221.pta.PtaPageRenderers;
import cn.xm1221.pta.core.book.BookConverter;
import cn.xm1221.pta.core.book.BookLayout;
import cn.xm1221.pta.core.book.BookSource;
import cn.xm1221.pta.core.lang.Json5;
import cn.xm1221.pta.core.report.ConversionReport;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Generates the Ageratum guide documents for every Patchouli book that was found.
 *
 * <p>The books are read through the client's resource manager, which is how Patchouli reads them:
 * a book's content belongs to the pack stack, not to the mod that declares it, so a second mod
 * adding chapters to someone else's book is part of the same book. Language keys are merged the
 * same way, across every namespace, because that is where a contributing pack keeps its text.</p>
 *
 * <p>Only {@code book.json} comes from the owning mod's own file — it lives under {@code data/},
 * which a client resource manager does not serve.</p>
 *
 * <p>Content is read from {@code en_us}; Patchouli does the same, storing page text as language
 * keys rather than duplicating whole entries per language. Only the text lookup differs per output
 * language.</p>
 */
public final class PtaGuideDocuments {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DEFAULT_LANGUAGE = "en_us";

    private PtaGuideDocuments() {
    }

    /**
     * Builds every mirror document.
     *
     * @return document location to its UTF-8 text, empty when nothing is configured
     */
    public static Map<ResourceLocation, String> build() {
        return buildFor(PtaBookList.effectiveBooks()).documents();
    }

    /**
     * Builds the mirror documents of specific books, with each book's conversion report.
     *
     * <p>The export command uses this shape: it wants the report that belongs to the book it was
     * asked for, not the log line a whole-run mirror would print.</p>
     *
     * @param books the books to mirror, in the order they should be generated
     */
    public static Build buildFor(List<ResourceLocation> books) {
        Map<ResourceLocation, String> documents = new LinkedHashMap<>();
        Map<ResourceLocation, String> reports = new LinkedHashMap<>();
        BookSource source = currentSource();
        Map<String, Map<String, String>> languages = new LinkedHashMap<>();

        for (ResourceLocation bookId : books) {
            try {
                mirror(bookId, source, languages, documents, reports);
            } catch (RuntimeException | IOException exception) {
                // One broken book must not stop the rest, or break the reload.
                LOGGER.error("[pta] failed to mirror Patchouli book {}", bookId, exception);
            }
        }
        return new Build(documents, reports);
    }

    /**
     * What one generation run produced.
     *
     * @param documents document location to its UTF-8 text
     * @param reports   book id to that book's conversion report, in Markdown
     */
    public record Build(Map<ResourceLocation, String> documents, Map<ResourceLocation, String> reports) {
    }

    /**
     * The book files as the game currently sees them.
     *
     * <p>During a reload the resource manager has already been switched to the new pack stack
     * ({@code ReloadableResourceManager.createReload} does that before any listener runs), so a
     * build that happens while the reload asks for documents reads exactly the books that reload
     * installed. When there is no manager yet — nothing has been reloaded, or this runs from the
     * export command before a world exists — the owning mod's own files are the fallback.</p>
     */
    private static BookSource currentSource() {
        Minecraft minecraft = Minecraft.getInstance();
        ResourceManager resources = minecraft == null ? null : minecraft.getResourceManager();
        return resources == null ? new ModFileBookSource() : new ResourceManagerBookSource(resources);
    }

    private static void mirror(ResourceLocation bookId, BookSource source,
                               Map<String, Map<String, String>> languageCache,
                               Map<ResourceLocation, String> out, Map<ResourceLocation, String> reports)
            throws IOException {
        String namespace = bookId.getNamespace();
        String bookName = bookId.getPath();

        BookLayout layout = BookLayout.load(source, namespace, bookName, DEFAULT_LANGUAGE);
        Set<String> languages = outputLanguages(namespace, source);

        ConversionReport merged = ConversionReport.empty();
        int written = 0;
        for (String language : languages) {
            Map<String, String> lang = languageCache.computeIfAbsent(language,
                    code -> language(source, namespace, code));

            BookConverter.Output output = BookConverter.convert(layout, lang, PtaPageRenderers.registry());
            mergeReports(merged, output.report());

            for (Map.Entry<String, String> document : output.documents().entrySet()) {
                ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
                        namespace, "ageratum/" + language + "/" + document.getKey() + ".md");
                out.put(location, document.getValue());
                written++;
            }
        }

        LOGGER.info("[pta] mirroring {} ({} categories, {} entries): {} documents (languages: {})",
                bookId, layout.categories().size(), layout.entries().size(), written,
                String.join(", ", languages));
        reports.put(bookId, merged.toMarkdown());
        if (!merged.isClean()) {
            LOGGER.info("[pta] conversion notes for {}:\n{}", bookId, merged.toMarkdown());
        }
    }

    /**
     * The language keys for one language, merged the way the game merges them: every namespace's
     * {@code assets/<namespace>/lang/<code>.json}, with the book's own namespace last so a book
     * always wins its own keys.
     *
     * <p>This is not optional. A pack that adds chapters to a book keeps their text in its own
     * language file, and without the merge those chapters would be drawn as raw keys.</p>
     */
    private static Map<String, String> language(BookSource source, String bookNamespace, String code) {
        Map<String, String> merged = new LinkedHashMap<>();
        List<String> namespaces = new ArrayList<>(source.namespaces());
        namespaces.remove(bookNamespace);
        namespaces.add(bookNamespace);

        for (String namespace : namespaces) {
            String text = source.read("assets/" + namespace + "/lang/" + code + ".json");
            if (text != null) {
                merged.putAll(Json5.flatten(text));
            }
        }
        return merged;
    }

    /**
     * The languages to write documents for: always {@code en_us}, which Ageratum falls back to,
     * plus the player's own language when anything in the stack ships text for it.
     */
    private static Set<String> outputLanguages(String namespace, BookSource source) {
        Set<String> languages = new LinkedHashSet<>();
        languages.add(DEFAULT_LANGUAGE);

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.getLanguageManager() != null) {
            String selected = minecraft.getLanguageManager().getSelected();
            if (selected != null && !selected.isBlank()) {
                String normalized = selected.toLowerCase(Locale.ROOT).replace('-', '_');
                if (shipsLanguage(source, namespace, normalized)) {
                    languages.add(normalized);
                }
            }
        }
        return languages;
    }

    private static boolean shipsLanguage(BookSource source, String bookNamespace, String code) {
        if (source.read("assets/" + bookNamespace + "/lang/" + code + ".json") != null) {
            return true;
        }
        for (String namespace : source.namespaces()) {
            if (!namespace.equals(bookNamespace)
                    && source.read("assets/" + namespace + "/lang/" + code + ".json") != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Folds one document set's findings into the running total.
     */
    private static void mergeReports(ConversionReport into, ConversionReport from) {
        from.droppedAnchors().forEach((key, count) -> repeat(count, () -> into.anchor(key)));
        from.hostedPageTypes().forEach((key, count) -> repeat(count, () -> into.hostedPageType(key)));
        from.multiRecipePages().forEach((key, count) -> repeat(count, () -> into.multiRecipe(key)));
        from.unknownCommands().forEach((key, count) -> repeat(count, () -> into.unknownCommand(key)));
        from.unrenderedTextFields().forEach((key, count) -> repeat(count, () -> into.unrenderedText(key)));
        repeat(from.droppedUnderlines(), into::underline);
        repeat(from.droppedPlayerNames(), into::playerName);
    }

    private static void repeat(int times, Runnable action) {
        for (int i = 0; i < times; i++) {
            action.run();
        }
    }
}
