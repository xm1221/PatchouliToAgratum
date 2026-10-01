package cn.xm1221.pta.client.source;

import cn.xm1221.pta.PtaBookList;
import cn.xm1221.pta.core.book.BookConverter;
import cn.xm1221.pta.core.book.BookLayout;
import cn.xm1221.pta.core.book.BookSource;
import cn.xm1221.pta.core.lang.Json5;
import cn.xm1221.pta.core.report.ConversionReport;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Generates the Ageratum guide documents for every configured Patchouli book.
 *
 * <p>Runs before any resource manager exists, because the results are served by a resource pack.
 * Everything it needs comes from the mod files themselves, so there is no ordering problem and no
 * dependency on the game's resource pipeline.</p>
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
        BookSource source = new ModFileBookSource();

        for (ResourceLocation bookId : books) {
            try {
                mirror(bookId, source, documents, reports);
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

    private static void mirror(ResourceLocation bookId, BookSource source,
                               Map<ResourceLocation, String> out, Map<ResourceLocation, String> reports)
            throws IOException {
        String namespace = bookId.getNamespace();
        String bookName = bookId.getPath();

        BookLayout layout = BookLayout.load(source, namespace, bookName, DEFAULT_LANGUAGE);
        Set<String> languages = outputLanguages(namespace, source);

        ConversionReport merged = ConversionReport.empty();
        int written = 0;
        for (String language : languages) {
            String langPath = "assets/" + namespace + "/lang/" + language + ".json";
            String langText = source.read(langPath);
            Map<String, String> lang = langText == null ? Map.of() : Json5.flatten(langText);

            BookConverter.Output output = BookConverter.convert(layout, lang);
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
     * The languages to write documents for: always {@code en_us}, which Ageratum falls back to,
     * plus the player's own language when the book ships one.
     */
    private static Set<String> outputLanguages(String namespace, BookSource source) {
        Set<String> languages = new LinkedHashSet<>();
        languages.add(DEFAULT_LANGUAGE);

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.getLanguageManager() != null) {
            String selected = minecraft.getLanguageManager().getSelected();
            if (selected != null && !selected.isBlank()) {
                String normalized = selected.toLowerCase(Locale.ROOT).replace('-', '_');
                if (source.read("assets/" + namespace + "/lang/" + normalized + ".json") != null) {
                    languages.add(normalized);
                }
            }
        }
        return languages;
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
