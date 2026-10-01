package cn.xm1221.pta.client.source;

import cn.xm1221.pta.core.book.BookSource;
import net.neoforged.fml.ModList;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Reads book files straight out of the mods that own them.
 *
 * <p>Two reasons not to go through the resource manager:</p>
 * <ul>
 *   <li>{@code book.json} lives under {@code data/}, and a client resource manager only serves
 *       {@code assets/}. Patchouli reads from the mod file for the same reason.</li>
 *   <li>The generated documents have to be produced <i>before</i> the resource manager exists,
 *       because they are served by a resource pack. Reading mod files directly removes that
 *       ordering problem entirely.</li>
 * </ul>
 *
 * <p>Paths are relative to the mod file's root, so both a jar (production, and mods dropped into
 * {@code run/mods}) and a directory (a mod under development) work through the same code:</p>
 *
 * <pre>
 * data/&lt;namespace&gt;/patchouli_books/&lt;book&gt;/book.json
 * assets/&lt;namespace&gt;/patchouli_books/&lt;book&gt;/&lt;language&gt;/entries/items/amethyst.json
 * assets/&lt;namespace&gt;/lang/en_us.json
 * </pre>
 */
public final class ModFileBookSource implements BookSource {
    @Override
    public String read(String resourcePath) {
        Path root = rootOf(namespaceOf(resourcePath));
        if (root == null) {
            return null;
        }
        Path file = root.resolve(resourcePath);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return null;
        }
    }

    @Override
    public List<String> list(String directory, String suffix) {
        Path root = rootOf(namespaceOf(directory));
        if (root == null) {
            return List.of();
        }
        Path dir = root.resolve(directory);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(suffix))
                    .forEach(path -> result.add(
                            root.relativize(path).toString().replace('\\', '/')));
        } catch (IOException exception) {
            return List.of();
        }
        result.sort(String::compareTo);
        return result;
    }

    /**
     * The mod file's own root, which is where {@code assets/} and {@code data/} live.
     *
     * <p>This is the lookup Patchouli uses on NeoForge to find its books.</p>
     */
    private static Path rootOf(String namespace) {
        if (namespace == null || namespace.isEmpty()) {
            return null;
        }
        var modFileInfo = ModList.get().getModFileById(namespace);
        if (modFileInfo == null) {
            return null;
        }
        try {
            return modFileInfo.getFile().getSecureJar().getRootPath();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /**
     * Extracts {@code <namespace>} from {@code assets/<namespace>/...} or {@code data/<namespace>/...}.
     */
    private static String namespaceOf(String resourcePath) {
        String normalized = resourcePath.replace('\\', '/');
        int start;
        if (normalized.startsWith("assets/")) {
            start = "assets/".length();
        } else if (normalized.startsWith("data/")) {
            start = "data/".length();
        } else {
            return null;
        }
        int slash = normalized.indexOf('/', start);
        return slash < 0 ? null : normalized.substring(start, slash);
    }

    @Override
    public String toString() {
        return "ModFileBookSource";
    }
}
