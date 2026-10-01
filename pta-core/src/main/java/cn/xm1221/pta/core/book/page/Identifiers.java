package cn.xm1221.pta.core.book.page;

import java.util.regex.Pattern;

/**
 * Resource locations, checked without Minecraft on the classpath.
 *
 * <p>Conversions that emit an id into Ageratum markup are handing it to a component that parses it
 * as a real resource location, so an id that cannot be one has to be recognised here and declined
 * rather than written out to fail later. This is the same shape the game accepts: a namespace of
 * lower-case letters, digits and the three separators, then a path that may also contain slashes.
 * </p>
 */
final class Identifiers {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    private Identifiers() {
    }

    /** Whether the text is a plain resource location with a namespace. */
    static boolean isValid(String id) {
        return id != null && ID.matcher(id).matches();
    }
}
