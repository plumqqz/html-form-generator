package shaif.htmlgenerator.htmlform;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Генератор HTML-форм и таблиц из Java-классов или {@code Map<String,Object>}.
 */
public final class FormGen {
    private static final Pattern AUTO_HIDDEN = Pattern.compile(
        "^(?i:(?:id|uuid))$|.*[a-z](?:Id|ID)$|.*[_.-](?i:(?:id|uuid))$");

    private final Class<?> type; private final Object instance;
    private final Map<String, Object> data;
    private final Map<String, String> labelOverrides = new HashMap<>();
    private final Map<String, String> tableLabelOverrides = new HashMap<>();
    private final Map<String, String> cssClassOverrides = new HashMap<>();
    private final Set<String> readonlyOverrides = new HashSet<>();
    private final Set<String> disabledOverrides = new HashSet<>();
    private final Map<String, List<Option>> staticOpts = new HashMap<>();
    private final Map<String, Option.Resolver> dynamicOpts = new HashMap<>();
    private final Map<String, Map<String, Object>> yamlFields = new LinkedHashMap<>();
    private final Map<Class<?>, List<Class<?>>> mixins = new LinkedHashMap<>();
    private String yamlSubmit, yamlAction, yamlMethod;

    private Map<String, List<String>> fieldErrors   = Collections.emptyMap();
    private Map<String, List<String>> fieldWarnings = Collections.emptyMap();
    private List<String> globalErrors   = Collections.emptyList();
    private List<String> globalWarnings = Collections.emptyList();

    private String formId;
    private final Map<String, String> formAttrs = new LinkedHashMap<>();
    private String csrfName, csrfToken;

    private FormGen(Class<?> type, Object instance, Map<String, Object> data) {
        this.type = type; this.instance = instance; this.data = data;
    }

    public static FormGen of(Class<?> type) { return new FormGen(Objects.requireNonNull(type), null, null); }
    public static FormGen of(Object instance) { return new FormGen(Objects.requireNonNull(instance).getClass(), instance, null); }
    public static FormGen ofData(Map<String, Object> data) { return new FormGen(null, null, new LinkedHashMap<>(Objects.requireNonNull(data))); }

    // ---------- fluent ----------
    public FormGen label(String field, String label) { labelOverrides.put(field, label); return this; }
    public FormGen tableLabel(String field, String label) { tableLabelOverrides.put(field, label); return this; }
    public FormGen cssClass(String field, String cssClass) { cssClassOverrides.put(field, cssClass); return this; }
    public FormGen readonly(String field) { readonlyOverrides.add(field); return this; }
    public FormGen disabled(String field) { disabledOverrides.add(field); return this; }
    public FormGen options(String field, String... raw) { return options(field, Arrays.stream(raw).map(Option::parse).collect(Collectors.toList())); }
    public FormGen options(String field, List<Option> opts) { staticOpts.put(field, new ArrayList<>(opts)); return this; }
    public FormGen options(String field, Option... opts) { return options(field, Arrays.asList(opts)); }
    public FormGen options(String field, Option.Resolver r) { dynamicOpts.put(field, r); return this; }

    public FormGen errors(Map<String, List<String>> map) { fieldErrors = map == null ? Collections.emptyMap() : map; return this; }
    public FormGen errors(List<String> list) { globalErrors = list == null ? Collections.emptyList() : list; return this; }
    public FormGen warnings(Map<String, List<String>> map) { fieldWarnings = map == null ? Collections.emptyMap() : map; return this; }
    public FormGen warnings(List<String> list) { globalWarnings = list == null ? Collections.emptyList() : list; return this; }
    public List<String> globalErrors()   { return globalErrors; }
    public List<String> globalWarnings() { return globalWarnings; }

    /** Зарегистрировать mixin-класс для текущего типа. */
    public FormGen mixin(Class<?> mixinClass) {
        if (type != null) mixins.computeIfAbsent(type, k -> new ArrayList<>()).add(mixinClass);
        return this;
    }

    public FormGen formId(String id) { this.formId = id; return this; }
    public FormGen formAttr(String name, String value) { formAttrs.put(name, value); return this; }

    public FormGen csrf(String token) { this.csrfName = "_csrf"; this.csrfToken = token; return this; }
    public FormGen csrf(String name, String token) { this.csrfName = name; this.csrfToken = token; return this; }
    public String csrfHtml() {
        if (csrfToken == null) return null;
        return "<input type=\"hidden\" name=\"" + esc(csrfName) + "\" value=\"" + esc(csrfToken) + "\"/>";
    }

    @SuppressWarnings("unchecked")
    public FormGen yaml(Map<String, Object> spec) {
        if (spec == null) return this;
        spec = YamlSpec.resolveRefs(spec);
        if (spec.containsKey("submitLabel")) yamlSubmit = String.valueOf(spec.get("submitLabel"));
        if (spec.containsKey("action"))      yamlAction = String.valueOf(spec.get("action"));
        if (spec.containsKey("method"))      yamlMethod = String.valueOf(spec.get("method"));
        Object f = spec.get("fields");
        if (f instanceof Map) {
            for (Map.Entry<String, Object> e : ((Map<String, Object>) f).entrySet()) {
                Map<String, Object> merged = yamlFields.getOrDefault(e.getKey(), new LinkedHashMap<>());
                if (e.getValue() instanceof Map) merged.putAll((Map<String, Object>) e.getValue());
                yamlFields.put(e.getKey(), merged);
            }
        }
        return this;
    }

    // ---------- Control / Column ----------
    public static final class Control {
        public final String name, label;
        public final boolean required, readonly, disabled;
        private final boolean hidden;
        private final String html;
        private final List<String> errors;
        private final List<String> warnings;
        Control(String name, String label, boolean required, boolean readonly, boolean disabled,
                boolean hidden, String html, List<String> errors, List<String> warnings) {
            this.name = name; this.label = label; this.required = required;
            this.readonly = readonly; this.disabled = disabled;
            this.hidden = hidden; this.html = html;
            this.errors = errors == null ? Collections.emptyList() : errors;
            this.warnings = warnings == null ? Collections.emptyList() : warnings;
        }
        public String html() { return html; }
        public boolean isHidden() { return hidden; }
        public List<String> errors() { return errors; }
        public List<String> warnings() { return warnings; }
        public boolean hasErrors() { return !errors.isEmpty(); }
        public boolean hasWarnings() { return !warnings.isEmpty(); }
        public static Control of(String name, String label, String html) {
            return new Control(name, label, false, false, false, false, html, null, null);
        }
        public static Control hidden(String name, String html) {
            return new Control(name, "", false, false, false, true, html, null, null);
        }
        @Override public String toString() { return label.isEmpty() ? name : label; }
    }

    public static final class Column {
        public final String name, label;
        private final Function<Object, String> cell;
        Column(String name, String label, Function<Object, String> cell) { this.name = name; this.label = label; this.cell = cell; }
        public String cell(Object row) { return cell.apply(row); }
        public static Column of(String name, String label, Function<Object, String> cell) { return new Column(name, label, cell); }
        @Override public String toString() { return label.isEmpty() ? name : label; }
    }

    // ---------- form ----------
    public String form() { return form(Layout.VERTICAL); }
    public String form(Layout layout) { return form(layout, yamlAction, yamlMethod, yamlSubmit != null ? yamlSubmit : "Save"); }
    public String form(String action, String method) { return form(Layout.VERTICAL, action, method, yamlSubmit != null ? yamlSubmit : "Save"); }
    public String form(String action, String method, String submit) { return form(Layout.VERTICAL, action, method, submit); }

    public String form(Layout layout, String action, String method, String submit) {
        StringBuilder sb = new StringBuilder("<form");
        if (formId != null) sb.append(" id=\"").append(esc(formId)).append("\"");
        if (action != null) sb.append(" action=\"").append(esc(action)).append("\"");
        if (method != null) sb.append(" method=\"").append(esc(method)).append("\"");
        for (Map.Entry<String, String> e : formAttrs.entrySet()) {
            sb.append(" ").append(e.getKey());
            if (e.getValue() != null) sb.append("=\"").append(esc(e.getValue())).append("\"");
        }
        if (layout == Layout.HORIZONTAL) sb.append(" class=\"form-horizontal\"");
        if (layout == Layout.INLINE)     sb.append(" class=\"form-inline\"");
        sb.append(">\n");
        if (!globalErrors.isEmpty()) {
            sb.append("  <div class=\"form-errors\" role=\"alert\">\n");
            for (String e : globalErrors) sb.append("    <span class=\"error\">").append(esc(e)).append("</span>\n");
            sb.append("  </div>\n");
        }
        if (!globalWarnings.isEmpty()) {
            sb.append("  <div class=\"form-warnings\">\n");
            for (String w : globalWarnings) sb.append("    <span class=\"warning\">").append(esc(w)).append("</span>\n");
            sb.append("  </div>\n");
        }
        for (Control c : controls()) sb.append(renderControl(layout, c));
        if (submit != null) sb.append("  <button type=\"submit\">").append(esc(submit)).append("</button>\n");
        return sb.append("</form>\n").toString();
    }

    public List<Control> controls() {
        List<Control> out = new ArrayList<>();
        if (csrfToken != null) out.add(Control.hidden(csrfName, csrfHtml()));
        for (Meta m : metas(false)) out.add(controlFor(m, configFor(m)));
        return out;
    }

    public List<Control> visibleControls() {
        List<Control> out = new ArrayList<>();
        for (Control c : controls()) if (!c.isHidden()) out.add(c);
        return out;
    }

    public List<Control> hiddenControls() {
        List<Control> out = new ArrayList<>();
        for (Control c : controls()) if (c.isHidden()) out.add(c);
        return out;
    }

    private Control controlFor(Meta m, FieldConfig cfg) {
        if (cfg.type == FieldType.HIDDEN) {
            String html = "<input type=\"hidden\" name=\"" + esc(m.name)
                        + "\" value=\"" + esc(fmt(m.value)) + "\"/>";
            return Control.hidden(m.name, html);
        }
        String id = "f-" + m.name;
        return new Control(m.name, cfg.effectiveLabel(false), cfg.isRequired(),
                           cfg.isReadonly(), cfg.isDisabled(), false,
                           renderFieldHtml(m, cfg, id),
                           fieldErrors.getOrDefault(m.name, Collections.emptyList()),
                           fieldWarnings.getOrDefault(m.name, Collections.emptyList()));
    }

    private static String renderControl(Layout layout, Control c) {
        if (c.isHidden()) return "  " + c.html() + "\n";
        String req = c.required ? " <span class=\"required\">*</span>" : "";
        String id  = "f-" + c.name;
        String cls = "field";
        if (c.hasErrors()) cls += " has-error";
        else if (c.hasWarnings()) cls += " has-warning";
        String msgs = messagesHtml(c, layout == Layout.INLINE);
        switch (layout) {
            case HORIZONTAL:
                return "  <div class=\"" + cls + " row\">\n"
                     + "    <label for=\"" + esc(id) + "\" class=\"col-label\">" + esc(c.label) + req + "</label>\n"
                     + "    <div class=\"col-control\">\n" + indent2(c.html()) + msgs + "\n    </div>\n"
                     + "  </div>\n";
            case INLINE:
                return "  <span class=\"" + cls + "\"><label for=\"" + esc(id) + "\" class=\"sr-only\">"
                     + esc(c.label) + "</label> " + c.html().stripLeading() + msgs + "</span>\n";
            default:
                return "  <div class=\"" + cls + "\">\n"
                     + "    <label for=\"" + esc(id) + "\">" + esc(c.label) + req + "</label>\n"
                     + c.html() + msgs + "\n"
                     + "  </div>\n";
        }
    }
    private static String messagesHtml(Control c, boolean inline) {
        if (c.errors().isEmpty() && c.warnings().isEmpty()) return "";
        String sep = inline ? " " : "\n    ";
        StringBuilder sb = new StringBuilder();
        for (String e : c.errors())   sb.append(sep).append("<span class=\"error\">").append(esc(e)).append("</span>");
        for (String w : c.warnings()) sb.append(sep).append("<span class=\"warning\">").append(esc(w)).append("</span>");
        return sb.toString();
    }
    private static String indent2(String html) {
        return html.lines().map(l -> l.isEmpty() ? l : "  " + l).collect(Collectors.joining("\n"));
    }

    private static boolean supportsReadonly(FieldType t) { return t != FieldType.SELECT && t != FieldType.CHECKBOX && t != FieldType.RADIO; }
    private static String roAttr(FieldConfig cfg) { return cfg.isReadonly() && supportsReadonly(cfg.type) ? " readonly" : ""; }
    private static String disAttr(FieldConfig cfg) { return cfg.isDisabled() ? " disabled" : ""; }

    private String renderFieldHtml(Meta m, FieldConfig cfg, String id) {
        Object v = m.value;
        switch (cfg.type) {
            case CHECKBOX: {
                StringBuilder sb = new StringBuilder("    <input type=\"checkbox\" id=\"").append(id)
                    .append("\" name=\"").append(m.name).append("\"");
                sb.append(clsAttr(cfg)).append(disAttr(cfg));
                if (Boolean.TRUE.equals(v)) sb.append(" checked");
                if (cfg.isRequired()) sb.append(" required");
                return sb.append("/>").toString();
            }
            case RADIO: {
                List<Option> opts = optionsFor(m, cfg);
                String cur = fmt(v);
                List<String> lines = new ArrayList<>();
                for (Option o : opts) {
                    lines.add("    <label class=\"radio\"><input type=\"radio\" name=\"" + m.name
                        + "\" value=\"" + esc(o.value) + "\"" + clsAttr(cfg) + disAttr(cfg)
                        + (o.value.equals(cur) ? " checked" : "")
                        + (o.disabled ? " disabled" : "") + "/>" + esc(o.label) + "</label>");
                }
                return String.join("\n", lines);
            }
            case SELECT: return selectHtml(m, cfg, id, v);
            case TEXTAREA: {
                StringBuilder sb = new StringBuilder("    <textarea id=\"").append(id)
                    .append("\" name=\"").append(m.name).append("\"");
                sb.append(clsAttr(cfg)).append(roAttr(cfg)).append(disAttr(cfg));
                if (cfg.isRequired()) sb.append(" required");
                if (!cfg.placeholder.isEmpty()) sb.append(" placeholder=\"").append(esc(cfg.placeholder)).append("\"");
                return sb.append(attrs(cfg)).append(">").append(esc(fmt(v))).append("</textarea>").toString();
            }
            default: {
                StringBuilder sb = new StringBuilder("    <input type=\"").append(cfg.type.inputType())
                    .append("\" id=\"").append(id).append("\" name=\"").append(m.name)
                    .append("\" value=\"").append(esc(fmt(v))).append("\"");
                sb.append(clsAttr(cfg)).append(roAttr(cfg)).append(disAttr(cfg));
                if (cfg.isRequired()) sb.append(" required");
                if (!cfg.placeholder.isEmpty()) sb.append(" placeholder=\"").append(esc(cfg.placeholder)).append("\"");
                return sb.append(attrs(cfg)).append("/>").toString();
            }
        }
    }

    private String selectHtml(Meta m, FieldConfig cfg, String id, Object v) {
        List<Option> opts = optionsFor(m, cfg);
        Set<String> cur = asStringSet(v);
        StringBuilder sb = new StringBuilder("    <select id=\"").append(id).append("\" name=\"").append(m.name).append("\"");
        sb.append(clsAttr(cfg)).append(disAttr(cfg));
        if (cfg.isRequired()) sb.append(" required");
        if (cfg.isMultiple()) sb.append(" multiple");
        sb.append(">\n");
        boolean showEmpty = cfg.emptyOption != null ? cfg.emptyOption : (!cfg.isMultiple() && !cfg.isRequired());
        if (showEmpty) sb.append("      <option value=\"\"></option>\n");
        for (Option o : opts) if (o.group == null) appendOption(sb, "      ", o, cur);
        Map<String, List<Option>> byGroup = new LinkedHashMap<>();
        for (Option o : opts) if (o.group != null) byGroup.computeIfAbsent(o.group, k -> new ArrayList<>()).add(o);
        for (Map.Entry<String, List<Option>> e : byGroup.entrySet()) {
            sb.append("      <optgroup label=\"").append(esc(e.getKey())).append("\">\n");
            for (Option o : e.getValue()) appendOption(sb, "        ", o, cur);
            sb.append("      </optgroup>\n");
        }
        return sb.append("    </select>").toString();
    }

    private static void appendOption(StringBuilder sb, String indent, Option o, Set<String> cur) {
        sb.append(indent).append("<option value=\"").append(esc(o.value)).append("\"");
        if (cur.contains(o.value)) sb.append(" selected");
        if (o.disabled) sb.append(" disabled");
        sb.append(">").append(esc(o.label)).append("</option>\n");
    }
    private static Set<String> asStringSet(Object v) {
        if (v == null) return Collections.emptySet();
        if (v instanceof Collection<?>) {
            Set<String> s = new HashSet<>();
            for (Object o : (Collection<?>) v) if (o != null) s.add(fmt(o));
            return s;
        }
        return Collections.singleton(fmt(v));
    }

    // ---------- table ----------
    public Table table(List<?> rows)   { return new Table(rows); }
    public Table table(Object... rows) { return new Table(Arrays.asList(rows)); }

    public final class Table {
        private final List<?> rows;
        private String link;
        private List<String> columnNames;
        private final Map<String, String> colLinks = new HashMap<>();
        private final Map<String, Function<Object, String>> cells = new HashMap<>();
        private final List<Object[]> actions = new ArrayList<>();
        private String empty = "", cssClass = "data-table";
        private String caption;
        private String[] footer;
        private Function<Object, String> rowClass;
        private final Map<String, Function<Object, String>> cellClasses = new HashMap<>();
        private boolean zebra;
        Table(List<?> rows) { this.rows = rows; }
        public Table cssClass(String c) { this.cssClass = c; return this; }
        public Table zebra() { this.zebra = true; return this; }
        public Table link(String tpl) { this.link = tpl; return this; }
        public Table link(String col, String tpl) { colLinks.put(col, tpl); return this; }
        public Table columns(String... names) { this.columnNames = Arrays.asList(names); return this; }
        public Table label(String field, String label) { FormGen.this.label(field, label); return this; }
        public Table tableLabel(String field, String label) { FormGen.this.tableLabel(field, label); return this; }
        public Table cell(String f, Function<Object, String> r) { cells.put(f, r); return this; }
        public Table action(String label, Function<Object, String> url) { actions.add(new Object[]{label, url}); return this; }
        public Table empty(String msg) { this.empty = msg; return this; }
        public Table caption(String c) { this.caption = c; return this; }
        public Table footer(String... cells) { this.footer = cells; return this; }
        public Table rowClass(Function<Object, String> fn) { this.rowClass = fn; return this; }
        public Table cellClass(String field, String cls) { cellClasses.put(field, r -> cls); return this; }
        public Table cellClass(String field, Function<Object, String> fn) { cellClasses.put(field, fn); return this; }
        public List<Column> columns() {
            List<Column> out = new ArrayList<>();
            for (Meta m : metasForTable(this)) {
                FieldConfig cfg = configFor(m);
                out.add(new Column(m.name, cfg.effectiveLabel(true), row -> FormGen.this.cell(m, row, Table.this)));
            }
            return out;
        }
        public String html() { return renderTable(this); }
        @Override public String toString() { return html(); }
    }

    private String renderTable(Table t) {
        List<Meta> ms = metasForTable(t);
        StringBuilder sb = new StringBuilder("<table class=\"").append(esc(t.cssClass)).append("\">\n");
        if (t.caption != null) sb.append("  <caption>").append(esc(t.caption)).append("</caption>\n");
        sb.append("  <thead>\n    <tr>\n");
        for (Meta m : ms) sb.append("      <th>").append(esc(configFor(m).effectiveLabel(true))).append("</th>\n");
        if (!t.actions.isEmpty()) sb.append("      <th></th>\n");
        sb.append("    </tr>\n  </thead>\n  <tbody>\n");
        if (t.rows == null || t.rows.isEmpty()) {
            if (!t.empty.isEmpty()) sb.append("    <tr class=\"empty\"><td colspan=\"")
                .append(ms.size() + t.actions.size()).append("\">").append(esc(t.empty)).append("</td></tr>\n");
        } else {
            int i = 0;
            for (Object row : t.rows) {
                String zebra = t.zebra ? ((i & 1) == 0 ? "odd" : "even") : null;
                String extra = t.rowClass == null ? null : t.rowClass.apply(row);
                String trCls = joinCls(zebra, extra);
                sb.append("    <tr");
                if (trCls != null) sb.append(" class=\"").append(esc(trCls)).append("\"");
                sb.append(">\n");
                for (Meta m : ms) {
                    String cc = null;
                    Function<Object, String> ccFn = t.cellClasses.get(m.name);
                    if (ccFn != null) cc = ccFn.apply(row);
                    sb.append("      <td");
                    if (cc != null && !cc.isEmpty()) sb.append(" class=\"").append(esc(cc)).append("\"");
                    sb.append(">").append(cell(m, row, t)).append("</td>\n");
                }
                if (!t.actions.isEmpty()) {
                    sb.append("      <td>");
                    for (Object[] a : t.actions) {
                        @SuppressWarnings("unchecked") Function<Object, String> fn = (Function<Object, String>) a[1];
                        String url = fn.apply(row);
                        if (url != null) sb.append("<a href=\"").append(esc(url)).append("\">").append(esc((String) a[0])).append("</a> ");
                    }
                    sb.append("</td>\n");
                }
                sb.append("    </tr>\n"); i++;
            }
        }
        sb.append("  </tbody>\n");
        if (t.footer != null) {
            sb.append("  <tfoot>\n    <tr>\n");
            for (String f : t.footer) sb.append("      <td>").append(esc(f)).append("</td>\n");
            if (!t.actions.isEmpty()) sb.append("      <td></td>\n");
            sb.append("    </tr>\n  </tfoot>\n");
        }
        return sb.append("</table>\n").toString();
    }

    private static String joinCls(String a, String b) {
        boolean ea = a == null || a.isEmpty();
        boolean eb = b == null || b.isEmpty();
        if (ea && eb) return null;
        if (ea) return b;
        if (eb) return a;
        return a + " " + b;
    }

    private String cell(Meta m, Object row, Table t) {
        if (t.cells.containsKey(m.name)) { String out = t.cells.get(m.name).apply(row); return out == null ? "" : out; }
        Object v = readNamed(row, m.name);
        String text = esc(fmt(v));
        String tpl = t.colLinks.get(m.name); if (tpl == null) tpl = t.link;
        if (tpl != null) {
            Matcher mt = Pattern.compile("\\{(\\w+)\\}").matcher(tpl);
            StringBuilder sb = new StringBuilder();
            while (mt.find()) {
                String key = mt.group(1);
                Object val = key.equals("_") ? v : readNamed(row, key);
                mt.appendReplacement(sb, Matcher.quoteReplacement(val == null ? "" : fmt(val)));
            }
            mt.appendTail(sb);
            return "<a href=\"" + esc(sb.toString()) + "\">" + text + "</a>";
        }
        return text;
    }

    // ---------- metadata ----------
    private static final class Meta {
        final String name; final Class<?> javaType; final FormField annotation; final Object value;
        Meta(String n, Class<?> t, FormField a, Object v) { name = n; javaType = t; annotation = a; value = v; }
    }

    private List<Meta> metas(boolean forTable) {
        List<Meta> all = new ArrayList<>();
        if (type != null) {
            for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass())
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) continue;

                    boolean ignored = f.isAnnotationPresent(FormIgnore.class);
                    FormField a = f.getAnnotation(FormField.class);

                    List<Class<?>> mixinList = mixins.getOrDefault(type, Collections.emptyList());
                    for (Class<?> mx : mixinList) {
                        Field mf = findField(mx, f.getName());
                        if (mf != null) {
                            if (mf.isAnnotationPresent(FormIgnore.class)) ignored = true;
                            FormField ma = mf.getAnnotation(FormField.class);
                            if (ma != null) a = ma;
                        }
                    }
                    if (ignored) continue;

                    Object v = instance != null ? read(f, instance) : null;
                    all.add(new Meta(f.getName(), f.getType(), a, v));
                }
        } else {
            for (Map.Entry<String, Object> e : data.entrySet()) {
                Object v = e.getValue();
                Class<?> t = v == null ? String.class : v.getClass();
                all.add(new Meta(e.getKey(), t, null, v));
            }
        }
        List<Meta> out = new ArrayList<>();
        for (Meta m : all) {
            FieldConfig cfg = configFor(m);
            if (forTable) {
                if (cfg.type == FieldType.HIDDEN && cfg.tableVisible == null) continue;
                if (!cfg.isTableVisible()) continue;
            } else {
                if (!cfg.isVisible()) continue;
            }
            out.add(m);
        }
        out.sort(Comparator.comparing(
            (Meta m) -> { String o = configFor(m).order; return o == null ? "" : o; },
            (a, b) -> {
                if (a.isEmpty() && b.isEmpty()) return 0;
                if (a.isEmpty()) return 1;
                if (b.isEmpty()) return -1;
                return a.compareTo(b);
            }));
        return out;
    }

    private List<Meta> metasForTable(Table t) {
        if (t.columnNames == null) return metas(true);
        List<Meta> out = new ArrayList<>();
        for (String n : t.columnNames) {
            Class<?> jt = String.class;
            if (data != null && data.get(n) != null) jt = data.get(n).getClass();
            out.add(new Meta(n, jt, null, null));
        }
        return out;
    }

    private FieldConfig configFor(Meta m) {
        FieldConfig cfg = FieldConfig.fromAnnotation(m.annotation);
        for (Map<String, Object> yaml : YamlSpec.configsFor(yamlFields, m.name)) {
            cfg.applyYaml(yaml);
        }
        if (labelOverrides.containsKey(m.name)) cfg.label = labelOverrides.get(m.name);
        if (tableLabelOverrides.containsKey(m.name)) cfg.tableLabel = tableLabelOverrides.get(m.name);
        if (cssClassOverrides.containsKey(m.name)) cfg.cssClass = cssClassOverrides.get(m.name);
        if (readonlyOverrides.contains(m.name)) cfg.readonly = true;
        if (disabledOverrides.contains(m.name)) cfg.disabled = true;
        if (cfg.label == null || cfg.label.isEmpty()) cfg.label = humanize(m.name);
        if (cfg.type == null || cfg.type == FieldType.AUTO) cfg.type = detectType(m);
        if (isAutoHidden(m.name) && !explicitType(m.annotation, yamlFieldsFor(m.name)) && !hasOptions(m.name))
            cfg.type = FieldType.HIDDEN;
        return cfg;
    }

    private Map<String, Object> yamlFieldsFor(String name) {
        List<Map<String, Object>> all = YamlSpec.configsFor(yamlFields, name);
        if (all.isEmpty()) return null;
        Map<String, Object> merged = new LinkedHashMap<>();
        for (Map<String, Object> one : all) merged.putAll(one);
        return merged;
    }

    private boolean hasOptions(String name) { return staticOpts.containsKey(name) || dynamicOpts.containsKey(name); }
    private static boolean explicitType(FormField a, Map<String, Object> yaml) {
        return (a != null && a.type() != FieldType.AUTO) || (yaml != null && yaml.containsKey("type"));
    }
    private FieldType detectType(Meta m) {
        if (hasOptions(m.name)) return FieldType.SELECT;
        Class<?> t = m.javaType;
        if (t == boolean.class || t == Boolean.class) return FieldType.CHECKBOX;
        if (t.isEnum()) return FieldType.SELECT;
        if (t == LocalDate.class || t == java.sql.Date.class || t == Date.class) return FieldType.DATE;
        if (t == LocalDateTime.class || t == OffsetDateTime.class || t == Instant.class) return FieldType.DATETIME;
        if (t == LocalTime.class) return FieldType.TIME;
        if (t == int.class || t == long.class || t == double.class || t == float.class
            || Number.class.isAssignableFrom(t)) return FieldType.NUMBER;
        return FieldType.TEXT;
    }
    private static boolean isAutoHidden(String n) { return AUTO_HIDDEN.matcher(n).matches(); }

    public static String humanize(String field) {
        String[] parts = field.replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                              .replaceAll("[_\\-]+", " ").trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) if (!p.isEmpty())
            sb.append(sb.length() == 0 ? "" : " ").append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        return sb.toString();
    }

    private static String clsAttr(FieldConfig cfg) {
        return cfg.cssClass == null || cfg.cssClass.isEmpty() ? "" : " class=\"" + esc(cfg.cssClass) + "\"";
    }

    private List<Option> optionsFor(Meta m, FieldConfig cfg) {
        if (dynamicOpts.containsKey(m.name)) return dynamicOpts.get(m.name).resolve(new Option.Ctx(m.name, m.javaType, instance));
        if (staticOpts.containsKey(m.name)) return staticOpts.get(m.name);
        if (cfg.options != null) return cfg.options;
        if (m.javaType.isEnum()) return enumOptions(m.javaType);
        return Collections.emptyList();
    }
    private List<Option> enumOptions(Class<?> ec) {
        Object[] cs = ec.getEnumConstants();
        List<Option> out = new ArrayList<>(cs.length);
        for (Object c : cs) {
            String name = ((Enum<?>) c).name();
            FormField a;
            try { a = ec.getField(name).getAnnotation(FormField.class); }
            catch (NoSuchFieldException e) { a = null; }
            if (a == null) out.add(new Option(name));
            else out.add(new Option(a.value().isEmpty() ? name : a.value(),
                    a.label().isEmpty() ? name : a.label(),
                    a.group().isEmpty() ? null : a.group(), a.disabled()));
        }
        return out;
    }
    private String attrs(FieldConfig cfg) {
        StringBuilder sb = new StringBuilder();
        if (!cfg.pattern.isEmpty()) {
            sb.append(" pattern=\"").append(esc(cfg.pattern)).append("\"");
            if (!cfg.patternMessage.isEmpty()) sb.append(" title=\"").append(esc(cfg.patternMessage)).append("\"");
        }
        if (cfg.minLength != null) sb.append(" minlength=\"").append(cfg.minLength).append("\"");
        if (cfg.maxLength != null) sb.append(" maxlength=\"").append(cfg.maxLength).append("\"");
        return sb.toString();
    }

    private static Object readNamed(Object obj, String name) {
        if (obj instanceof Map) return ((Map<?, ?>) obj).get(name);
        for (Class<?> c = obj.getClass(); c != null && c != Object.class; c = c.getSuperclass())
            try { return read(c.getDeclaredField(name), obj); }
            catch (NoSuchFieldException ignored) {}
        return null;
    }
    private static Field findField(Class<?> c, String name) {
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            try { return k.getDeclaredField(name); }
            catch (NoSuchFieldException ignored) {}
        }
        return null;
    }

    private static Object read(Field f, Object obj) {
        try { f.setAccessible(true); return f.get(obj); } catch (Exception e) { return null; }
    }
    private static String fmt(Object o) {
        if (o == null) return "";
        if (o instanceof LocalDate || o instanceof LocalTime || o instanceof LocalDateTime) return o.toString();
        if (o instanceof Enum<?>) {
            Enum<?> e = (Enum<?>) o;
            try {
                FormField a = e.getDeclaringClass().getField(e.name()).getAnnotation(FormField.class);
                if (a != null && !a.value().isEmpty()) return a.value();
            } catch (NoSuchFieldException ignored) {}
            return e.name();
        }
        if (o instanceof Collection<?>) return ((Collection<?>) o).stream().map(FormGen::fmt).collect(Collectors.joining(", "));
        return o.toString();
    }
    public static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
