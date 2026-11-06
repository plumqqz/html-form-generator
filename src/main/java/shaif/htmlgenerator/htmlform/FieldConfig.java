package shaif.htmlgenerator.htmlform;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Мутабельная конфигурация поля (package-private). */
final class FieldConfig {
    String label, tableLabel;
    FieldType type;
    Boolean visible, tableVisible, required, multiple, emptyOption, readonly, disabled;
    List<Option> options;
    String placeholder = "", pattern = "", patternMessage = "", cssClass = "";
    Integer minLength, maxLength;
    String order;

    static FieldConfig fromAnnotation(FormField a) {
        FieldConfig c = new FieldConfig();
        if (a == null) return c;
        if (!a.label().isEmpty()) c.label = a.label();
        if (!a.tableLabel().isEmpty()) c.tableLabel = a.tableLabel();
        if (a.type() != FieldType.AUTO) c.type = a.type();
        if (!a.visible()) c.visible = false;
        if (!a.tableVisible()) c.tableVisible = false;
        if (a.required()) c.required = true;
        if (a.multiple()) c.multiple = true;
        if (a.readonly()) c.readonly = true;
        if (a.disabled()) c.disabled = true;
        if (a.emptyOption() == FormField.EmptyMode.YES) c.emptyOption = true;
        else if (a.emptyOption() == FormField.EmptyMode.NO) c.emptyOption = false;
        if (a.options().length > 0) c.options = Arrays.stream(a.options()).map(Option::parse).collect(java.util.stream.Collectors.toList());
        if (!a.placeholder().isEmpty()) c.placeholder = a.placeholder();
        if (!a.cssClass().isEmpty()) c.cssClass = a.cssClass();
        if (!a.pattern().isEmpty()) c.pattern = a.pattern();
        if (!a.patternMessage().isEmpty()) c.patternMessage = a.patternMessage();
        if (a.minLength() > 0) c.minLength = a.minLength();
        if (a.maxLength() > 0) c.maxLength = a.maxLength();
        if (!a.order().isEmpty()) c.order = a.order();
        return c;
    }
    void applyYaml(Map<String, Object> m) {
        if (m.containsKey("label")) label = str(m.get("label"));
        if (m.containsKey("tableLabel")) tableLabel = str(m.get("tableLabel"));
        if (m.containsKey("type")) type = FieldType.valueOf(str(m.get("type")).toUpperCase());
        if (m.containsKey("visible")) visible = bool(m.get("visible"));
        if (m.containsKey("tableVisible")) tableVisible = bool(m.get("tableVisible"));
        if (m.containsKey("required")) required = bool(m.get("required"));
        if (m.containsKey("multiple")) multiple = bool(m.get("multiple"));
        if (m.containsKey("readonly")) readonly = bool(m.get("readonly"));
        if (m.containsKey("disabled")) disabled = bool(m.get("disabled"));
        if (m.containsKey("emptyOption")) emptyOption = bool(m.get("emptyOption"));
        if (m.containsKey("placeholder")) placeholder = str(m.get("placeholder"));
        if (m.containsKey("cssClass")) cssClass = str(m.get("cssClass"));
        if (m.containsKey("pattern")) pattern = str(m.get("pattern"));
        if (m.containsKey("patternMessage")) patternMessage = str(m.get("patternMessage"));
        if (m.containsKey("minLength")) minLength = ((Number) m.get("minLength")).intValue();
        if (m.containsKey("maxLength")) maxLength = ((Number) m.get("maxLength")).intValue();
        if (m.containsKey("order")) { Object v = m.get("order"); order = v == null ? "" : String.valueOf(v); }
        if (m.containsKey("options")) {
            @SuppressWarnings("unchecked") List<Object> raw = (List<Object>) m.get("options");
            options = raw.stream().map(String::valueOf).map(Option::parse).collect(java.util.stream.Collectors.toList());
        }
    }
    boolean isVisible()      { return visible == null || visible; }
    boolean isTableVisible() { return tableVisible == null || tableVisible; }
    boolean isRequired()     { return Boolean.TRUE.equals(required); }
    boolean isMultiple()     { return Boolean.TRUE.equals(multiple); }
    boolean isReadonly()     { return Boolean.TRUE.equals(readonly); }
    boolean isDisabled()     { return Boolean.TRUE.equals(disabled); }
    String effectiveLabel(boolean forTable) {
        if (forTable && tableLabel != null && !tableLabel.isEmpty()) return tableLabel;
        return label;
    }
    private static String str(Object o)   { return o == null ? "" : String.valueOf(o); }
    private static Boolean bool(Object o) { return o instanceof Boolean ? (Boolean) o : Boolean.valueOf(str(o)); }
}
