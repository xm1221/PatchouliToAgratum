package cn.xm1221.pta.core.book.page;

import cn.xm1221.pta.core.book.BookLayout;
import cn.xm1221.pta.core.book.BookTextConverter;
import cn.xm1221.pta.core.report.ConversionReport;

import java.util.Map;

/**
 * Everything a {@link PageRenderer} needs to render one page.
 *
 * <p>The renderer never touches the raw JSON directly for text: field access goes through
 * {@link #localised} so that language keys are resolved, and through {@link #renderText} so that
 * macros, {@code $(...)} commands and internal links are handled the same way everywhere.</p>
 */
public final class PageRenderContext {
    private final BookLayout layout;
    private final BookLayout.Entry entry;
    private final int pageIndex;
    private final Map<?, ?> page;
    private final Map<String, String> lang;
    private final BookTextConverter texts;

    public PageRenderContext(BookLayout layout, BookLayout.Entry entry, int pageIndex,
                             Map<?, ?> page, Map<String, String> lang, BookTextConverter texts) {
        this.layout = layout;
        this.entry = entry;
        this.pageIndex = pageIndex;
        this.page = page;
        this.lang = lang;
        this.texts = texts;
    }

    /**
     * The page's type, always qualified.
     *
     * <p>Patchouli lets a book omit the namespace — {@code "text"} means {@code "patchouli:text"} —
     * so renderers only ever have to match one form.</p>
     */
    public String type() {
        String type = raw("type");
        if (type == null || type.isBlank()) {
            return "patchouli:text";
        }
        return type.contains(":") ? type : "patchouli:" + type;
    }

    public BookLayout layout() {
        return this.layout;
    }

    public BookLayout.Entry entry() {
        return this.entry;
    }

    public int pageIndex() {
        return this.pageIndex;
    }

    public Map<?, ?> page() {
        return this.page;
    }

    public ConversionReport report() {
        return this.texts.report();
    }

    /** The document the page is being rendered into. */
    public String documentPath() {
        return this.entry.documentPath();
    }

    /**
     * A raw field value.
     *
     * @return the value as text, or {@code null} when absent or not a string
     */
    public String raw(String field) {
        Object value = this.page.get(field);
        return value instanceof String text ? text : null;
    }

    /**
     * A field value resolved through the book's language table.
     *
     * @return the resolved text, or an empty string when absent
     */
    public String localised(String field) {
        return localise(raw(field));
    }

    /**
     * Resolves a language key when the book uses i18n, reporting keys that go nowhere.
     */
    public String localise(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        if (!this.layout.usesI18n()) {
            return key;
        }
        String value = this.lang.get(key);
        if (value == null) {
            this.texts.report().unrenderedText(key);
            return key;
        }
        return value;
    }

    /** The page's body text, resolved. */
    public String text() {
        return localised("text");
    }

    /** The page's heading, resolved; empty when the page has none. */
    public String title() {
        return localised("title");
    }

    /**
     * Converts book text for this page, rewriting links relative to this page's document.
     */
    public String renderText(String bookText) {
        return this.texts.convert(documentPath(), bookText);
    }
}
