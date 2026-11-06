package shaif.htmlgenerator.htmlform;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Загрузчик YAML-спецификаций.
 * Ключи в {@code fields} — регулярные выражения, порядок объявления важен.
 */
public final class YamlSpec {

    // ==================== Load from classpath ====================

    /** Загрузить базовый YAML рядом с классом и наложить локаль. */
    public static Map<String, Object> loadFromClasspath(Class<?> type, Locale locale) {
        return loadFromClasspath(type, type.getSimpleName(), locale);
    }

    /** Загрузить конкретный YAML по имени рядом с классом-якорем. */
    public static Map<String, Object> loadFromClasspath(Class<?> anchor, String name, Locale locale) {
        String base = name.replaceAll("\\.ya?ml$", "");
        Map<String, Object> m = resolveWith(base, locale, classpathSource(anchor), new HashSet<>());
        return m == null ? new LinkedHashMap<>() : resolveRefs(m);
    }

    /** Загрузить YAML по явному пути в classpath (от корня). */
    public static Map<String, Object> loadFromClasspath(String path, Locale locale) {
        String base = path.replaceAll("\\.ya?ml$", "");
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) cl = YamlSpec.class.getClassLoader();
        Map<String, Object> m = resolveWith(base, locale, loaderSource(cl), new HashSet<>());
        return m == null ? new LinkedHashMap<>() : resolveRefs(m);
    }

    // ==================== Load from filesystem / stream / string ====================

    /** Загрузить YAML из файла. {@code extends} ищется в той же директории. */
    public static Map<String, Object> load(Path path) throws IOException {
        Path abs = path.toAbsolutePath();
        Path dir = abs.getParent();
        String base = abs.getFileName().toString().replaceAll("\\.ya?ml$", "");
        Map<String, Object> m = resolveWith(base, null, pathSource(dir), new HashSet<>());
        return m == null ? new LinkedHashMap<>() : resolveRefs(m);
    }

    /**
     * Загрузить YAML из потока. Файловый {@code extends} не разрешается
     * (неизвестно, откуда брать родителя); полевой — разрешается.
     */
    public static Map<String, Object> load(InputStream in) {
        Map<String, Object> m = parse(in);
        return m == null ? new LinkedHashMap<>() : resolveRefs(m);
    }

    /** Загрузить YAML из строки. Файловый {@code extends} не разрешается. */
    public static Map<String, Object> loadFromString(String yaml) {
        return load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    }

    // ==================== Merge / resolveRefs ====================

    @SuppressWarnings("unchecked")
    public static Map<String, Object> merge(Map<String, Object> base, Map<String, Object> overlay) {
        if (base == null)    return overlay == null ? new LinkedHashMap<>() : new LinkedHashMap<>(overlay);
        if (overlay == null) return new LinkedHashMap<>(base);
        Map<String, Object> out = new LinkedHashMap<>(base);
        for (Map.Entry<String, Object> e : overlay.entrySet()) {
            String key = e.getKey(); Object bv = base.get(key); Object ov = e.getValue();
            if ("fields".equals(key) && bv instanceof Map && ov instanceof Map)
                out.put(key, mergeFields((Map<String, Object>) bv, (Map<String, Object>) ov));
            else out.put(key, ov);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mergeFields(Map<String, Object> base, Map<String, Object> overlay) {
        Map<String, Object> out = new LinkedHashMap<>(base);
        for (Map.Entry<String, Object> e : overlay.entrySet()) {
            Object bv = out.get(e.getKey()); Object ov = e.getValue();
            if (bv instanceof Map && ov instanceof Map) {
                Map<String, Object> merged = new LinkedHashMap<>((Map<String, Object>) bv);
                merged.putAll((Map<String, Object>) ov); out.put(e.getKey(), merged);
            } else out.put(e.getKey(), ov);
        }
        return out;
    }

    /** Разрешает полевой extends (по литеральному имени родителя). */
    public static Map<String, Object> resolveRefs(Map<String, Object> spec) {
        if (spec == null) return new LinkedHashMap<>();
        Object fieldsObj = spec.get("fields");
        if (!(fieldsObj instanceof Map)) return spec;
        @SuppressWarnings("unchecked") Map<String, Object> fields = (Map<String, Object>) fieldsObj;
        Map<String, Object> resolved = new LinkedHashMap<>();
        for (String name : fields.keySet())
            resolved.put(name, resolveField(name, fields, new HashSet<>()));
        Map<String, Object> out = new LinkedHashMap<>(spec); out.put("fields", resolved); return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> resolveField(String name, Map<String, Object> fields, Set<String> visiting) {
        if (!visiting.add(name)) throw new IllegalStateException("YAML field extends cycle: " + visiting + " → " + name);
        try {
            Object rawObj = fields.get(name); if (!(rawObj instanceof Map)) return new LinkedHashMap<>();
            Map<String, Object> raw = (Map<String, Object>) rawObj; Map<String, Object> out = new LinkedHashMap<>();
            Object parentObj = raw.get("extends");
            if (parentObj != null) {
                List<String> parents = (parentObj instanceof List) ? (List<String>) parentObj
                    : Collections.singletonList(String.valueOf(parentObj));
                for (String p : parents) { Map<String, Object> pr = resolveField(p, fields, visiting); if (pr != null) out.putAll(pr); }
            }
            for (Map.Entry<String, Object> e : raw.entrySet()) if (!"extends".equals(e.getKey())) out.put(e.getKey(), e.getValue());
            return out;
        } finally { visiting.remove(name); }
    }

    // ==================== Internals ====================

    @FunctionalInterface
    private interface Source {
        InputStream open(String baseName, Locale locale) throws IOException;
    }

    private static Source classpathSource(Class<?> anchor) {
        return (base, loc) -> {
            for (String c : candidates(base, loc)) {
                InputStream in = anchor.getResourceAsStream(c);
                if (in != null) return in;
            }
            return null;
        };
    }

    private static Source loaderSource(ClassLoader cl) {
        return (base, loc) -> {
            for (String c : candidates(base, loc)) {
                InputStream in = cl.getResourceAsStream(c);
                if (in != null) return in;
            }
            return null;
        };
    }

    private static Source pathSource(Path dir) {
        return (base, loc) -> {
            for (String c : candidates(base, loc)) {
                Path p = dir.resolve(c);
                if (Files.exists(p)) return Files.newInputStream(p);
            }
            return null;
        };
    }

    private static Map<String, Object> resolveWith(String name, Locale locale, Source src, Set<String> visiting) {
        if (!visiting.add(name)) throw new IllegalStateException("YAML file extends cycle: " + visiting + " → " + name);
        try {
            Map<String, Object> base = readFrom(src, name, null);
            Map<String, Object> loc  = (locale != null && !locale.getLanguage().isEmpty()) ? readFrom(src, name, locale) : null;
            Map<String, Object> own;
            if (base == null && loc == null) return null;
            else if (base == null) own = loc;
            else if (loc == null) own = base;
            else own = merge(base, loc);

            Object ext = own.get("extends");
            if (ext == null) return own;

            List<String> parentNames = new ArrayList<>();
            if (ext instanceof List) {
                for (Object o : (List<?>) ext) parentNames.add(String.valueOf(o).replaceAll("\\.ya?ml$", ""));
            } else {
                parentNames.add(String.valueOf(ext).replaceAll("\\.ya?ml$", ""));
            }

            Map<String, Object> result = new LinkedHashMap<>();
            for (String pn : parentNames) {
                Map<String, Object> parent = resolveWith(pn, locale, src, visiting);
                if (parent == null) throw new IllegalStateException(
                        "YAML extends target not found: '" + pn + "' (from '" + name + "')");
                result = merge(result, parent);
            }

            Map<String, Object> ownCopy = new LinkedHashMap<>(own); ownCopy.remove("extends");
            return merge(result, ownCopy);
        } finally { visiting.remove(name); }
    }

    private static Map<String, Object> readFrom(Source src, String name, Locale locale) {
        try (InputStream in = src.open(name, locale)) {
            return in == null ? null : parse(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(InputStream in) {
        return (Map<String, Object>) new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
    }

    private static List<String> candidates(String base, Locale l) {
        List<String> out = new ArrayList<>();
        if (l != null && !l.getLanguage().isEmpty()) {
            if (!l.getCountry().isEmpty()) out.add(base + "_" + l.getLanguage() + "_" + l.getCountry() + ".yaml");
            out.add(base + "_" + l.getLanguage() + ".yaml");
        }
        out.add(base + ".yaml");
        return out;
    }

    // ==================== Regex keys ====================

    /** Полное совпадение регекса с именем поля. Битый регекс → false. */
    public static boolean keyMatches(String pattern, String fieldName) {
        if (pattern == null || fieldName == null) return false;
        if (pattern.equals(fieldName)) return true;
        try {
            return Pattern.matches(pattern, fieldName);
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    /** Все конфиги из `fields`, применимые к полю, в порядке объявления. */
    public static List<Map<String, Object>> configsFor(
            Map<String, Map<String, Object>> fields, String fieldName) {
        if (fields == null || fields.isEmpty()) return Collections.emptyList();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> e : fields.entrySet()) {
            if (e.getValue() != null && keyMatches(e.getKey(), fieldName)) {
                out.add(e.getValue());
            }
        }
        return out;
    }
}
