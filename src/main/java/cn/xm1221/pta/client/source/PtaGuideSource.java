package cn.xm1221.pta.client.source;

import cn.xm1221.pta.PtaConfig;
import cn.xm1221.pta.core.book.BookConverter;
import cn.xm1221.pta.core.book.BookLayout;
import cn.xm1221.pta.core.book.BookSource;
import cn.xm1221.pta.core.lang.Json5;
import cn.xm1221.pta.core.report.ConversionReport;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Produces Ageratum guide documents from Patchouli books, at resource reload time.
 *
 * <p>The documents are synthesised during the reload rather than shipped in the jar, so they
 * follow the installed book instead of a snapshot of it. They are served under the namespace of
 * the mod that owns the book, which is why {@code /ageratum hexcasting} shows the mirrored book
 * and why several books can coexist without colliding.</p>
 *
 * <p>Content itself is still read out of {@code en_us}; Patchouli does the same, storing page
 * text as language keys rather than duplicating whole entries per language. Only the text lookup
 * differs per output language.</p>
 */
public final class PtaGuideSource {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DEFAULT_LANGUAGE = "en_us";

    private PtaGuideSource() {
    }

    /**
     * Builds every mirrored document the caller asked for.
     *
     * @param manager    the resource manager being reloaded
     * @param pathPrefix the directory the caller listed, so only matching documents are added
     * @param filter     the predicate the caller would have applied, honoured so this cannot
     *                   leak resources the caller did not ask for
     * @return synthetic resources keyed by their location
     */
    public static Map<ResourceLocation, Resource> documents(ResourceManager manager,
                                                            String pathPrefix,
                                                            Predicate<ResourceLocation> filter) {
        Map<ResourceLocation, Resource> result = new LinkedHashMap<>();
        String prefix = pathPrefix.endsWith("/") ? pathPrefix : pathPrefix + "/";
        BookSource source = new ResourceManagerBookSource(manager);
        PackResources realPack = manager.listPacks().findFirst().orElse(null);

        List<ResourceLocation> books;
        try {
            books = configuredBooks();
        } catch (RuntimeException exception) {
            LOGGER.warn("[pta] could not read the configured book list", exception);
            return result;
        }

        for (ResourceLocation bookId : books) {
            try {
                mirror(bookId, source, realPack, prefix, filter, result);
            } catch (RuntimeException | IOException exception) {
                // One broken book must not take the whole reload down with it.
                LOGGER.error("[pta] failed to mirror Patchouli book {}", bookId, exception);
            }
        }
        return result;
    }

    private static void mirror(ResourceLocation bookId, BookSource source, PackResources realPack,
                               String prefix, Predicate<ResourceLocation> filter,
                               Map<ResourceLocation, Resource> out) throws IOException {
        String namespace = bookId.getNamespace();
        String bookName = bookId.getPath();

        BookLayout layout = BookLayout.load(source, namespace, bookName, DEFAULT_LANGUAGE);
        LOGGER.info("[pta] mirroring {} ({} categories, {} entries)",
                bookId, layout.categories().size(), layout.entries().size());

        ConversionReport merged = ConversionReport.empty();
        int written = 0;
        for (String language : outputLanguages(namespace, source)) {
            String langPath = "assets/" + namespace + "/lang/" + language + ".json";
            String langText = source.read(langPath);
            Map<String, String> lang = langText == null ? Map.of() : Json5.flatten(langText);

            BookConverter.Output output = BookConverter.convert(layout, lang);
            mergeReports(merged, output.report());

            for (Map.Entry<String, String> document : output.documents().entrySet()) {
                ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
                        namespace, "ageratum/" + language + "/" + document.getKey() + ".md");
                if (!location.getPath().startsWith(prefix) || !filter.test(location)) {
                    continue;
                }
                byte[] bytes = document.getValue().getBytes(StandardCharsets.UTF_8);
                out.put(location, new Resource(realPack, () -> new ByteArrayInputStream(bytes)));
                written++;
            }
        }

        LOGGER.info("[pta] {} documents generated for {}", written, bookId);
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
                String normalized = selected.toLowerCase(java.util.Locale.ROOT).replace('-', '_');
                if (source.read("assets/" + namespace + "/lang/" + normalized + ".json") != null) {
                    languages.add(normalized);
                }
            }
        }
        return languages;
    }

    private static List<ResourceLocation> configuredBooks() {
        List<ResourceLocation> configured = PtaConfig.books();
        return configured == null ? discoverBooks() : configured;
    }

    /**
     * Finds every book Patchouli could load, for the {@code *} configuration.
     */
    private static List<ResourceLocation> discoverBooks() {
        List<ResourceLocation> found = new ArrayList<>();
        for (var info : net.neoforged.fml.ModList.get().getMods()) {
            String namespace = info.getModId();
            var modFileInfo = net.neoforged.fml.ModList.get().getModFileById(namespace);
            if (modFileInfo == null) {
                continue;
            }
            Path root = modFileInfo.getFile().findResource(BookSource.NAMESPACE_BOOKS);
            if (root == null || !Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> books = Files.list(root)) {
                books.filter(Files::isDirectory)
                        .map(path -> path.getFileName().toString())
                        .map(name -> ResourceLocation.fromNamespaceAndPath(namespace, name))
                        .forEach(found::add);
            } catch (IOException exception) {
                LOGGER.debug("[pta] could not list books of {}", namespace, exception);
            }
        }
        found.sort((a, b) -> a.toString().compareTo(b.toString()));
        return found;
    }

    /**
     * Folds one document set's findings into the running total.
     */
    private static void mergeReports(ConversionReport into, ConversionReport from) {
        from.droppedAnchors().forEach((key, count) -> {
            for (int i = 0; i < count; i++) {
                into.anchor(key);
            }
        });
        from.unsupportedPageTypes().forEach((key, count) -> {
            for (int i = 0; i < count; i++) {
                into.unsupportedPageType(key);
            }
        });
        from.unknownCommands().forEach((key, count) -> {
            for (int i = 0; i < count; i++) {
                into.unknownCommand(key);
            }
        });
        from.unrenderedTextFields().forEach((key, count) -> {
            for (int i = 0; i < count; i++) {
                into.unrenderedText(key);
            }
        });
        for (int i = 0; i < from.droppedUnderlines(); i++) {
            into.underline();
        }
        for (int i = 0; i < from.droppedPlayerNames(); i++) {
            into.playerName();
        }
    }
}
