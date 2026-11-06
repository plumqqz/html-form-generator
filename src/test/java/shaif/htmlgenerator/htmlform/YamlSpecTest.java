package shaif.htmlgenerator.htmlform;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class YamlSpecTest {
    @Test void formGenConsumesSpec() {
        Map<String,Object> spec = Map.of("submitLabel","Send","fields", Map.of("name", Map.of("label","Имя","required",true)));
        assertTrue(FormGen.of(Object.class).yaml(spec).form().contains("Send"));
    }
    @Test void mergeKeepsTypeFromBase() {
        Map<String,Object> base = new LinkedHashMap<>();
        base.put("fields", new LinkedHashMap<>(Map.of("role", new LinkedHashMap<>(Map.of("type","SELECT","options",List.of("A","B"),"label","Role")))));
        Map<String,Object> ru = new LinkedHashMap<>();
        ru.put("fields", new LinkedHashMap<>(Map.of("role", new LinkedHashMap<>(Map.of("label","Роль")))));
        Map<String,Object> merged = YamlSpec.merge(base, ru);
        @SuppressWarnings("unchecked")
        Map<String,Object> role = (Map<String,Object>) ((Map<String,Object>) merged.get("fields")).get("role");
        assertEquals("Роль", role.get("label"));
        assertEquals("SELECT", role.get("type"));
        assertEquals(List.of("A","B"), role.get("options"));
    }
    @Test void resolveRefsInlinesParent() {
        Map<String,Object> spec = new LinkedHashMap<>();
        spec.put("fields", new LinkedHashMap<>(Map.of(
            "email", Map.of("pattern",".+@.+","label","Email"),
            "customerEmail", Map.of("extends","email","label","Customer email"))));
        Map<String,Object> out = YamlSpec.resolveRefs(spec);
        @SuppressWarnings("unchecked")
        Map<String,Object> c = (Map<String,Object>) ((Map<String,Object>) out.get("fields")).get("customerEmail");
        assertEquals("Customer email", c.get("label"));
        assertEquals(".+@.+", c.get("pattern"));
        assertFalse(c.containsKey("extends"));
    }
    @Test void resolveRefsCycleThrows() {
        Map<String,Object> spec = new LinkedHashMap<>();
        spec.put("fields", new LinkedHashMap<>(Map.of(
            "a", Map.of("extends","b"), "b", Map.of("extends","a"))));
        assertThrows(IllegalStateException.class, () -> YamlSpec.resolveRefs(spec));
    }
    @Test void resolveRefsMultipleParents() {
        Map<String,Object> spec = new LinkedHashMap<>();
        spec.put("fields", new LinkedHashMap<>(Map.of(
            "a", Map.of("label","A","order","001"),
            "b", Map.of("placeholder","B"),
            "c", Map.of("extends", List.of("a","b")))));
        Map<String,Object> out = YamlSpec.resolveRefs(spec);
        @SuppressWarnings("unchecked")
        Map<String,Object> c = (Map<String,Object>) ((Map<String,Object>) out.get("fields")).get("c");
        assertEquals("A", c.get("label"));
        assertEquals("001", c.get("order"));
        assertEquals("B", c.get("placeholder"));
    }
    @Test void multiFileExtendsMergesBoth() {
        Map<String, Object> spec = YamlSpec.loadFromClasspath(shaif.htmlgenerator.Order.class, Locale.forLanguageTag("ru"));
        @SuppressWarnings("unchecked")
        Map<String, Object> fields = (Map<String, Object>) spec.get("fields");
        assertTrue(fields.containsKey("firstName"));
        assertTrue(fields.containsKey("email"));
        assertTrue(fields.containsKey("amount"));
        assertTrue(fields.containsKey("currency"));
        assertTrue(fields.containsKey("number"));
        assertTrue(fields.containsKey("status"));
    }
    @Test void multiFileExtendsOwnWins() {
        Map<String, Object> spec = YamlSpec.loadFromClasspath(shaif.htmlgenerator.Order.class, Locale.forLanguageTag("ru"));
        @SuppressWarnings("unchecked")
        Map<String, Object> fields = (Map<String, Object>) spec.get("fields");
        @SuppressWarnings("unchecked")
        Map<String, Object> status = (Map<String, Object>) fields.get("status");
        assertEquals("Статус", status.get("label"));
        assertEquals("SELECT", status.get("type"));
    }
    @Test void keyMatchesExact() {
        assertTrue(YamlSpec.keyMatches("firstName", "firstName"));
        assertFalse(YamlSpec.keyMatches("firstName", "lastName"));
    }
    @Test void keyMatchesRegex() {
        assertTrue(YamlSpec.keyMatches(".*Id$", "userId"));
        assertTrue(YamlSpec.keyMatches(".*Id$", "orderId"));
        assertFalse(YamlSpec.keyMatches(".*Id$", "name"));
        assertTrue(YamlSpec.keyMatches("email|contactEmail", "email"));
        assertTrue(YamlSpec.keyMatches("email|contactEmail", "contactEmail"));
        assertFalse(YamlSpec.keyMatches("email|contactEmail", "phone"));
    }
    @Test void keyMatchesBrokenRegexIsFalse() {
        assertFalse(YamlSpec.keyMatches("[", "x"));
        assertFalse(YamlSpec.keyMatches("(", "x"));
    }
    @Test void loadFromStringParses() {
        Map<String, Object> spec = YamlSpec.loadFromString(
                "submitLabel: Send\nfields:\n  email:\n    label: Email\n    required: true\n");
        assertEquals("Send", spec.get("submitLabel"));
        @SuppressWarnings("unchecked")
        Map<String, Object> fields = (Map<String, Object>) spec.get("fields");
        assertTrue(fields.containsKey("email"));
    }

    @Test void loadFromStringResolvesFieldExtends() {
        Map<String, Object> spec = YamlSpec.loadFromString(
                "fields:\n  email:\n    label: Email\n    pattern: '.+@.+'\n  customerEmail:\n    extends: email\n    required: true\n");
        @SuppressWarnings("unchecked")
        Map<String, Object> fields = (Map<String, Object>) spec.get("fields");
        @SuppressWarnings("unchecked")
        Map<String, Object> c = (Map<String, Object>) fields.get("customerEmail");
        assertEquals(".+@.+", c.get("pattern"));
        assertEquals(true, c.get("required"));
    }

    @Test void loadFromPathResolvesFileExtends() throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("yamlspec");
        java.nio.file.Files.writeString(dir.resolve("Base.yaml"),
                "fields:\n  email:\n    label: Email\n    pattern: '.+@.+'\n");
        java.nio.file.Files.writeString(dir.resolve("User.yaml"),
                "extends: Base\nfields:\n  name:\n    label: Name\n");
        Map<String, Object> spec = YamlSpec.load(dir.resolve("User.yaml"));
        @SuppressWarnings("unchecked")
        Map<String, Object> fields = (Map<String, Object>) spec.get("fields");
        assertTrue(fields.containsKey("email"));
        assertTrue(fields.containsKey("name"));
    }

    @Test void ofDataWithLoadFromString() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("email", "x@y.z");
        String html = FormGen.ofData(data)
                .yaml(YamlSpec.loadFromString(
                        "fields:\n  email:\n    label: Почта\n    required: true\n"))
                .form();
        assertTrue(html.contains("Почта"));
        assertTrue(html.contains("required"));
    }
}
