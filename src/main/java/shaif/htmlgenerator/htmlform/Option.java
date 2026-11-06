package shaif.htmlgenerator.htmlform;
import java.util.List;
import java.util.Objects;

/** Одна опция {@code <select>}. */
public final class Option {
    public final String value, label, group;
    public final boolean disabled;

    public Option(String v) { this(v, v, null, false); }
    public Option(String v, String l) { this(v, l, null, false); }
    public Option(String v, String l, String g) { this(v, l, g, false); }
    public Option(String v, String l, String g, boolean d) {
        this.value = Objects.requireNonNull(v); this.label = l == null ? v : l;
        this.group = g; this.disabled = d;
    }

    /** Парсит "value" или "value=label". */
    public static Option parse(String raw) {
        int eq = raw.indexOf('=');
        return eq < 0 ? new Option(raw.trim())
                      : new Option(raw.substring(0, eq).trim(), raw.substring(eq + 1).trim());
    }

    /** Динамический источник опций (лямбда). */
    @FunctionalInterface public interface Resolver { List<Option> resolve(Ctx ctx); }

    /** Контекст для Resolver. */
    public static final class Ctx {
        public final String fieldName; public final Class<?> fieldType; public final Object owner;
        public Ctx(String n, Class<?> t, Object o) { fieldName = n; fieldType = t; owner = o; }

        /** Типизированный владелец. Возвращает null, если не совпадает. */
        public <T> T owner(Class<T> type) {
            return type.isInstance(owner) ? type.cast(owner) : null;
        }
    }
}
