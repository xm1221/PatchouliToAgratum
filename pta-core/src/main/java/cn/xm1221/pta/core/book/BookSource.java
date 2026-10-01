package cn.xm1221.pta.core.book;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The raw files of one Patchouli book, addressed the way a resource manager would address them.
 *
 * <p>Keys are full resource paths such as
 * {@code data/hexcasting/patchouli_books/thehexbook/book.json} and
 * {@code assets/hexcasting/patchouli_books/thehexbook/en_us/entries/items/amethyst.json}.</p>
 *
 * <p>On the client this is backed by the real {@code ResourceManager}; for tests and the CLI it
 * is backed by a checkout on disk. Keeping the two behind one type is what lets the whole
 * pipeline be exercised offline against a real book.</p>
 */
public interface BookSource {
    String NAMESPACE_BOOKS = "patchouli_books";

    /**
     * Reads one file.
     *
     * @return the file's text, or {@code null} when it does not exist
     */
    String read(String resourcePath);

    /**
     * Lists resource paths under a directory.
     *
     * @param directory directory prefix, without a trailing slash
     * @param suffix    required suffix, e.g. {@code .json}
     */
    List<String> list(String directory, String suffix);

    /**
     * A source backed by an in-memory map, for tests and for wrapping a resource manager.
     */
    static BookSource of(Map<String, String> files) {
        Map<String, String> copy = new LinkedHashMap<>(files);
        return new BookSource() {
            @Override
            public String read(String resourcePath) {
                return copy.get(resourcePath);
            }

            @Override
            public List<String> list(String directory, String suffix) {
                String prefix = directory.endsWith("/") ? directory : directory + "/";
                List<String> result = new ArrayList<>();
                for (String path : copy.keySet()) {
                    if (path.startsWith(prefix) && path.endsWith(suffix)) {
                        result.add(path);
                    }
                }
                result.sort(String::compareTo);
                return result;
            }

            @Override
            public String toString() {
                return "BookSource[" + copy.size() + " files]";
            }
        };
    }

    /**
     * A source backed by a mod's resource directories on disk.
     *
     * @param resourcesRoot a directory holding {@code assets/} and {@code data/}, i.e. a mod's
     *                      {@code src/main/resources}
     * @param namespace     the mod id owning the book
     * @param bookName      the book's folder name, e.g. {@code thehexbook}
     */
    static BookSource ofDirectory(Path resourcesRoot, String namespace, String bookName) {
        String bookRoot = NAMESPACE_BOOKS + "/" + bookName + "/";
        String dataRoot = "data/" + namespace + "/" + bookRoot;
        String assetRoot = "assets/" + namespace + "/" + bookRoot;
        return new DirectoryBookSource(List.of(resourcesRoot), dataRoot + ", " + assetRoot);
    }

    /**
     * A source backed by several resource directories, read in order.
     *
     * <p>A mod under development keeps generated data — the recipes among it — in a directory of
     * its own rather than beside its hand-written files, so what a book refers to can be spread
     * over more than one root. The first root that has a file wins, which is the order a resource
     * pack stack uses.</p>
     *
     * @param resourcesRoots directories holding {@code assets/} and {@code data/}, most important
     *                       first
     */
    static BookSource ofDirectories(List<Path> resourcesRoots) {
        return new DirectoryBookSource(List.copyOf(resourcesRoots), null);
    }

    /**
     * Resource directories on disk, addressed the way a resource manager addresses them.
     *
     * @param roots the directories, in lookup order
     * @param label what the source calls itself, or {@code null} to list the roots
     */
    record DirectoryBookSource(List<Path> roots, String label) implements BookSource {
        @Override
        public String read(String resourcePath) {
            for (Path root : this.roots) {
                Path file = root.resolve(resourcePath);
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                try {
                    return Files.readString(file, StandardCharsets.UTF_8);
                } catch (IOException exception) {
                    throw new UncheckedIOException("Failed to read " + file, exception);
                }
            }
            return null;
        }

        @Override
        public List<String> list(String directory, String suffix) {
            List<String> result = new ArrayList<>();
            for (Path root : this.roots) {
                Path start = root.resolve(directory);
                if (!Files.isDirectory(start)) {
                    continue;
                }
                try (Stream<Path> walk = Files.walk(start)) {
                    walk.filter(Files::isRegularFile)
                            .filter(path -> path.getFileName().toString().endsWith(suffix))
                            .map(path -> root.relativize(path).toString().replace('\\', '/'))
                            .filter(path -> !result.contains(path))
                            .forEach(result::add);
                } catch (IOException exception) {
                    throw new UncheckedIOException("Failed to walk " + start, exception);
                }
            }
            result.sort(String::compareTo);
            return result;
        }

        @Override
        public String toString() {
            return this.label != null ? "BookSource[" + this.label + "]" : "BookSource" + this.roots;
        }
    }
}
