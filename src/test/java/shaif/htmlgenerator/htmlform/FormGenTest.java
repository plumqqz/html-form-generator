package shaif.htmlgenerator.htmlform;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class FormGenTest {
    @SuppressWarnings("unused")
    static class E {
        private Long id;
        @FormField(label = "From annotation", order = "001") private String name;
        @FormField(required = true) private String email;
        @FormField(visible = false) private String secret;
        private boolean active;
        private Status status;
        private String customerEmail;
        void setName(String v) { this.name = v; }
    }
    enum Status {
        @FormField(label = "Active") ACTIVE,
        @FormField(label = "Blocked") BLOCKED,
        @FormField(label = "Archived", disabled = true) ARCHIVED;
    }
    private static FormGen.Control find(List<FormGen.Control> cs, String name) {
        return cs.stream().filter(c -> c.name.equals(name)).findFirst().orElseThrow();
    }
    @Test void rendersAnnotationLabel() { assertTrue(FormGen.of(new E()).form().contains("From annotation")); }
    @Test void yamlLabelWins() {
        String html = FormGen.of(E.class).yaml(Map.of("fields", Map.of("name", Map.of("label", "From YAML")))).form();
        assertTrue(html.contains("From YAML"));
        assertFalse(html.contains("From annotation"));
    }
    @Test void hiddenId() { assertTrue(FormGen.of(new E()).form().contains("<input type=\"hidden\" name=\"id\"")); }
    @Test void visibleFalseNotRendered() { assertFalse(FormGen.of(new E()).form().contains("name=\"secret\"")); }
    @Test void booleanIsCheckbox() { assertTrue(FormGen.of(new E()).form().contains("type=\"checkbox\"")); }
    @Test void enumIsSelectWithLabels() {
        String html = FormGen.of(new E()).form();
        assertTrue(html.contains("<select")); assertTrue(html.contains(">Active</option>"));
        assertTrue(html.contains(">Archived</option>")); assertTrue(html.contains("disabled"));
    }
    @Test void escapesValues() {
        E e = new E(); e.setName("<script>x</script>");
        assertTrue(FormGen.of(e).form().contains("&lt;script&gt;x&lt;/script&gt;"));
    }
    @Test void optionsProviderForcesSelect() {
        class P { String country; }
        String html = FormGen.of(new P()).options("country", new Option("RU","Россия")).form();
        assertTrue(html.contains("<select")); assertTrue(html.contains(">Россия</option>"));
    }
    @Test void regexRendersPatternAttribute() {
        String html = FormGen.of(E.class).yaml(Map.of("fields",
            Map.of("email", Map.of("pattern", ".+@.+", "patternMessage", "Bad")))).form();
        assertTrue(html.contains("pattern=\".+@.+\"")); assertTrue(html.contains("title=\"Bad\""));
    }
    @Test void humanize() {
        assertEquals("User Id", FormGen.humanize("userId"));
        assertEquals("First Name", FormGen.humanize("first_name"));
    }
    @Test void emptyOptionCanBeDisabled() {
        class P { String status; }
        String html = FormGen.of(P.class).yaml(Map.of("fields",
            Map.of("status", Map.of("type", "SELECT", "emptyOption", false, "options", List.of("A","B"))))).form();
        assertFalse(html.contains("<option value=\"\"></option>"));
    }
    @Test void multiValueSelectsMatchingOptions() {
        class P { List<String> perms; }
        P p = new P(); p.perms = Arrays.asList("read","write");
        String html = FormGen.of(p).yaml(Map.of("fields",
            Map.of("perms", Map.of("type","SELECT","multiple",true,"options", List.of("read","write","admin"))))).form();
        assertTrue(html.contains("value=\"read\" selected"));
        assertTrue(html.contains("value=\"write\" selected"));
        assertFalse(html.contains("value=\"admin\" selected"));
    }
    @Test void mapDataRendersTypes() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name","John"); data.put("age",30); data.put("active",true); data.put("birthDate",LocalDate.of(1990,5,17));
        String html = FormGen.ofData(data).form();
        assertTrue(html.contains("value=\"John\""));
        assertTrue(html.contains("type=\"number\""));
        assertTrue(html.contains("type=\"checkbox\""));
        assertTrue(html.contains("type=\"date\""));
    }
    @Test void mapDataAutoHidesIds() {
        assertTrue(FormGen.ofData(Map.of("id",42,"name","x")).form().contains("<input type=\"hidden\" name=\"id\""));
    }
    @Test void yamlExtendsInheritsPattern() {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("fields", new LinkedHashMap<>(Map.of(
            "email", Map.of("label","Email","pattern",".+@.+"),
            "customerEmail", Map.of("extends","email","label","Customer email","required",true))));
        String html = FormGen.of(E.class).yaml(spec).form();
        assertTrue(html.contains("Customer email")); assertTrue(html.contains("pattern=\".+@.+\""));
    }
    @Test void yamlSelectTypeWithoutProviderRendersEmptySelect() {
        class P { String role; }
        String html = FormGen.of(P.class).yaml(Map.of("fields", Map.of("role", Map.of("type","SELECT")))).form();
        assertTrue(html.contains("<select")); assertTrue(html.contains("<option value=\"\"></option>"));
    }
    @Test void formAndTableLabelsAreIndependent() {
        class P { String firstName; String email; }
        Map<String,Object> spec = new LinkedHashMap<>();
        spec.put("fields", new LinkedHashMap<>(Map.of(
            "firstName", Map.of("label","First name","tableLabel","Name"),
            "email", Map.of("label","Email"))));
        P p = new P(); p.firstName = "Ivan"; p.email = "i@x.com";
        String formHtml = FormGen.of(p).yaml(spec).form();
        String tableHtml = FormGen.of(P.class).yaml(spec).table(Arrays.asList(p)).html();
        assertTrue(formHtml.contains("First name"));
        assertFalse(formHtml.contains(">Name<"));
        assertTrue(tableHtml.contains("<th>Name</th>"));
        assertTrue(tableHtml.contains("<th>Email</th>"));
    }
    @Test void programmaticTableLabelOverridesYaml() {
        class P { String name; }
        P p = new P(); p.name = "Alice";
        String html = FormGen.of(P.class).yaml(Map.of("fields", Map.of("name", Map.of("label","YAML Label"))))
            .tableLabel("name","Col").table(Arrays.asList(p)).html();
        assertTrue(html.contains("<th>Col</th>"));
        assertFalse(html.contains("<th>YAML Label</th>"));
    }
    @Test void formLabelOnlyFallsBackToTable() {
        class P { String name; }
        P p = new P(); p.name = "Alice";
        String html = FormGen.of(P.class).yaml(Map.of("fields", Map.of("name", Map.of("label","Label"))))
            .table(Arrays.asList(p)).html();
        assertTrue(html.contains("<th>Label</th>"));
    }
    @Test void orderStringSortsBetween() {
        class P { String a; String mid; String b; }
        Map<String,Object> spec = new LinkedHashMap<>();
        spec.put("fields", new LinkedHashMap<>(Map.of(
            "b", Map.of("order","002"), "a", Map.of("order","001"), "mid", Map.of("order","001.005"))));
        String html = FormGen.of(P.class).yaml(spec).form();
        int pa = html.indexOf("name=\"a\""), pm = html.indexOf("name=\"mid\""), pb = html.indexOf("name=\"b\"");
        assertTrue(pa >= 0 && pa < pm && pm < pb);
    }
    @Test void orderAnnotationLexicographic() {
        class P {
            @FormField(order = "002") String a;
            @FormField(order = "001.005") String mid;
            @FormField(order = "003") String b;
        }
        String html = FormGen.of(P.class).form();
        assertTrue(html.indexOf("name=\"mid\"") < html.indexOf("name=\"a\""));
        assertTrue(html.indexOf("name=\"a\"") < html.indexOf("name=\"b\""));
    }
    @Test void orderIsLexicographic() {
        class P { String b; String a; }
        Map<String,Object> spec = new LinkedHashMap<>();
        spec.put("fields", new LinkedHashMap<>(Map.of(
            "b", Map.of("order","010"), "a", Map.of("order","002"))));
        String html = FormGen.of(P.class).yaml(spec).form();
        assertTrue(html.indexOf("name=\"a\"") < html.indexOf("name=\"b\""));
    }
    @Test void cssClassFromYamlAppliesToInput() {
        class P { String name; }
        String html = FormGen.of(P.class).yaml(Map.of("fields",
            Map.of("name", Map.of("cssClass", "form-control")))).form();
        assertTrue(html.contains("class=\"form-control\""));
    }
    @Test void cssClassProgrammaticOverride() {
        class P { String name; }
        String html = FormGen.of(P.class).yaml(Map.of("fields", Map.of("name", Map.of("cssClass","yaml-class"))))
            .cssClass("name","code-class").form();
        assertTrue(html.contains("class=\"code-class\""));
        assertFalse(html.contains("yaml-class"));
    }
    @Test void cssClassOnSelectAndTextarea() {
        class P { String role; String notes; }
        String html = FormGen.of(new P())
            .options("role", new Option("A","A"))
            .yaml(Map.of("fields", Map.of(
                "role", Map.of("cssClass","select2"),
                "notes", Map.of("type","TEXTAREA","cssClass","md-editor")))).form();
        assertTrue(html.contains("class=\"select2\""));
        assertTrue(html.contains("class=\"md-editor\""));
    }
    @Test void controlsIncludeHidden() {
        class P { String name; Long id; String email; }
        List<FormGen.Control> cs = FormGen.of(P.class).controls();
        assertEquals(3, cs.size());
        assertEquals(1, cs.stream().filter(FormGen.Control::isHidden).count());
    }
    @Test void visibleControlsReturnsOnlyVisible() {
        class P { String name; Long id; String uuid; }
        List<FormGen.Control> v = FormGen.of(P.class).visibleControls();
        assertEquals(1, v.size());
        assertEquals("name", v.get(0).name);
        assertTrue(v.stream().noneMatch(FormGen.Control::isHidden));
    }
    @Test void visibleControlsExcludesCsrf() {
        class P { String name; }
        List<FormGen.Control> v = FormGen.of(P.class).csrf("tok").visibleControls();
        assertEquals(1, v.size());
        assertEquals("name", v.get(0).name);
    }
    @Test void hiddenControlsReturnsOnlyHidden() {
        class P { String name; Long id; String uuid; }
        List<FormGen.Control> h = FormGen.of(P.class).hiddenControls();
        assertEquals(2, h.size());
        assertTrue(h.stream().allMatch(FormGen.Control::isHidden));
        Set<String> names = new HashSet<>();
        for (FormGen.Control c : h) names.add(c.name);
        assertTrue(names.contains("id"));
        assertTrue(names.contains("uuid"));
    }
    @Test void formHorizontalAddsClass() {
        assertTrue(FormGen.of(Object.class).form(Layout.HORIZONTAL).contains("class=\"form-horizontal\""));
    }
    @Test void formInlineAddsClass() {
        assertTrue(FormGen.of(Object.class).form(Layout.INLINE).contains("class=\"form-inline\""));
    }
    @Test void formVerticalHasNoLayoutClass() {
        String html = FormGen.of(Object.class).form(Layout.VERTICAL);
        assertFalse(html.contains("form-horizontal"));
        assertFalse(html.contains("form-inline"));
    }
    @Test void formIgnoreHidesFieldEverywhere() {
        class P {
            String name;
            @FormIgnore String secret;
            @FormField(visible = false) String hiddenInForm;
        }
        FormGen g = FormGen.of(P.class);

        Set<String> controlNames = new HashSet<>();
        for (FormGen.Control c : g.controls()) controlNames.add(c.name);
        assertFalse(controlNames.contains("secret"));
        assertFalse(controlNames.contains("hiddenInForm"));

        String form = g.form();
        assertFalse(form.contains("name=\"secret\""));
        assertFalse(form.contains("name=\"hiddenInForm\""));

        String table = g.table(Collections.emptyList()).html();
        assertFalse(table.contains("<th>Secret</th>"));
        assertTrue(table.contains("<th>Hidden In Form</th>"));
    }

    @Test void mixinProvidesAnnotations() {
        class Target { String email; }
        abstract class Mixin {
            @FormField(label = "Email from mixin", required = true)
            String email;
        }
        String html = FormGen.of(Target.class).mixin(Mixin.class).form();
        assertTrue(html.contains("Email from mixin"));
        assertTrue(html.contains("required"));
    }

    @Test void mixinCanIgnoreField() {
        class Target { String email; String secret; }
        abstract class Mixin {
            @FormIgnore String secret;
        }
        String html = FormGen.of(Target.class).mixin(Mixin.class).form();
        assertTrue(html.contains("name=\"email\""));
        assertFalse(html.contains("name=\"secret\""));
    }

    @Test void ownerTypedAccessor() {
        class User { String getCountry() { return "RU"; } }
        User u = new User();
        Option.Ctx ctx = new Option.Ctx("city", String.class, u);
        assertSame(u, ctx.owner(User.class));
        assertNull(ctx.owner(String.class));
        assertNull(new Option.Ctx("x", String.class, null).owner(User.class));
    }

    // ---------- errors / warnings ----------
    @Test void fieldErrorRenders() {
        class P { String email; }
        String html = FormGen.of(P.class).errors(Map.of("email", List.of("Required"))).form();
        assertTrue(html.contains("class=\"field has-error\""));
        assertTrue(html.contains("<span class=\"error\">Required</span>"));
    }
    @Test void fieldWarningRenders() {
        class P { String password; }
        String html = FormGen.of(P.class).warnings(Map.of("password", List.of("Истекает через 3 дня"))).form();
        assertTrue(html.contains("class=\"field has-warning\""));
        assertTrue(html.contains("<span class=\"warning\">Истекает через 3 дня</span>"));
    }
    @Test void bothErrorAndWarningRenders() {
        class P { String email; }
        String html = FormGen.of(P.class)
            .errors(Map.of("email", List.of("Required"))).warnings(Map.of("email", List.of("Deprecated"))).form();
        assertTrue(html.contains("class=\"field has-error\""));
        assertTrue(html.contains("<span class=\"error\">Required</span>"));
        assertTrue(html.contains("<span class=\"warning\">Deprecated</span>"));
    }
    @Test void multipleMessagesOnField() {
        class P { String email; }
        String html = FormGen.of(P.class).errors(Map.of("email", List.of("Required", "Invalid format"))).form();
        assertTrue(html.contains("<span class=\"error\">Required</span>"));
        assertTrue(html.contains("<span class=\"error\">Invalid format</span>"));
    }
    @Test void globalErrorRenders() {
        class P { String name; }
        String html = FormGen.of(P.class).errors(List.of("Passwords do not match")).form();
        assertTrue(html.contains("class=\"form-errors\""));
        assertTrue(html.contains("role=\"alert\""));
        assertTrue(html.contains("<span class=\"error\">Passwords do not match</span>"));
    }
    @Test void globalWarningRenders() {
        class P { String name; }
        String html = FormGen.of(P.class).warnings(List.of("Saved an hour ago")).form();
        assertTrue(html.contains("class=\"form-warnings\""));
        assertTrue(html.contains("<span class=\"warning\">Saved an hour ago</span>"));
    }
    @Test void errorsAreEscaped() {
        class P { String name; }
        String html = FormGen.of(P.class).errors(Map.of("name", List.of("<script>x</script>"))).form();
        assertTrue(html.contains("&lt;script&gt;x&lt;/script&gt;"));
        assertFalse(html.contains("<script>x</script>"));
    }
    @Test void errorsAvailableInControl() {
        class P { String name; String email; }
        List<FormGen.Control> cs = FormGen.of(P.class).errors(Map.of("name", List.of("A", "B"))).controls();
        FormGen.Control name = find(cs, "name");
        assertEquals(2, name.errors().size());
        assertTrue(name.hasErrors());
        FormGen.Control email = find(cs, "email");
        assertTrue(email.errors().isEmpty());
    }
    @Test void emptyErrorsMapClearsFieldErrors() {
        class P { String name; }
        FormGen g = FormGen.of(P.class).errors(Map.of("name", List.of("X")));
        String html = g.errors(Map.of()).form();
        assertFalse(html.contains("has-error"));
    }

    // ---------- readonly / disabled ----------
    @Test void readonlyFromYaml() {
        class P { String name; }
        String html = FormGen.of(P.class).yaml(Map.of("fields", Map.of("name", Map.of("readonly", true)))).form();
        assertTrue(html.contains("readonly"));
    }
    @Test void disabledFromAnnotation() {
        class P { @FormField(disabled = true) String name; }
        assertTrue(FormGen.of(P.class).form().contains("disabled"));
    }
    @Test void readonlyProgrammatic() {
        class P { String name; }
        assertTrue(FormGen.of(P.class).readonly("name").form().contains("readonly"));
    }
    @Test void disabledProgrammatic() {
        class P { String name; }
        assertTrue(FormGen.of(P.class).disabled("name").form().contains("disabled"));
    }
    @Test void readonlyNotRenderedOnSelect() {
        class P { String role; }
        String html = FormGen.of(P.class).options("role", new Option("A","A")).readonly("role").form();
        assertFalse(html.contains("readonly"));
    }
    @Test void controlExposesReadonlyDisabled() {
        class P { String a; String b; }
        List<FormGen.Control> cs = FormGen.of(P.class).readonly("a").disabled("b").controls();
        assertTrue(cs.stream().anyMatch(c -> c.name.equals("a") && c.readonly));
        assertTrue(cs.stream().anyMatch(c -> c.name.equals("b") && c.disabled));
    }

    // ---------- formId / formAttr ----------
    @Test void formIdRendered() {
        assertTrue(FormGen.of(Object.class).formId("my-form").form().contains("id=\"my-form\""));
    }
    @Test void formAttrRendered() {
        String html = FormGen.of(Object.class).formAttr("autocomplete", "off").formAttr("novalidate", null).form();
        assertTrue(html.contains("autocomplete=\"off\""));
        assertTrue(html.contains("novalidate"));
    }

    // ---------- csrf ----------
    @Test void csrfRenderedAsHidden() {
        class P { String name; }
        String html = FormGen.of(P.class).csrf("tok-123").form();
        assertTrue(html.contains("<input type=\"hidden\" name=\"_csrf\" value=\"tok-123\"/>"));
    }
    @Test void csrfCustomName() {
        String html = FormGen.of(Object.class).csrf("csrf_token", "xyz").form();
        assertTrue(html.contains("name=\"csrf_token\""));
        assertTrue(html.contains("value=\"xyz\""));
    }
    @Test void csrfInHiddenControls() {
        String csrfHtml = FormGen.of(Object.class).csrf("tok").csrfHtml();
        assertNotNull(csrfHtml);
        assertTrue(csrfHtml.contains("_csrf"));
    }
    @Test void csrfFirstInControls() {
        class P { String name; }
        List<FormGen.Control> cs = FormGen.of(P.class).csrf("t").controls();
        assertTrue(cs.get(0).isHidden());
        assertEquals("_csrf", cs.get(0).name);
    }

    // ---------- regex keys ----------
    @Test void regexKeyMatchesMultipleFields() {
        class P { String userRef; String orderRef; String name; }
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("fields", new LinkedHashMap<>(Map.of(".*Ref$", Map.of("required", true))));
        List<FormGen.Control> cs = FormGen.of(P.class).yaml(spec).visibleControls();
        assertTrue(find(cs, "userRef").required);
        assertTrue(find(cs, "orderRef").required);
        assertFalse(find(cs, "name").required);
    }

    @Test void laterKeyWinsRegardlessOfSpecificity() {
        class P { String userRef; String orderRef; }
        Map<String, Object> spec = new LinkedHashMap<>();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(".*Ref$",  Map.of("required", true));
        fields.put("userRef", Map.of("required", false));
        spec.put("fields", fields);
        List<FormGen.Control> cs = FormGen.of(P.class).yaml(spec).visibleControls();
        assertFalse(find(cs, "userRef").required);
        assertTrue(find(cs, "orderRef").required);
    }

    @Test void orderFlippedRegexWins() {
        class P { String userRef; }
        Map<String, Object> spec = new LinkedHashMap<>();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("userRef", Map.of("required", false));
        fields.put(".*Ref$", Map.of("required", true));
        spec.put("fields", fields);
        List<FormGen.Control> cs = FormGen.of(P.class).yaml(spec).visibleControls();
        assertTrue(find(cs, "userRef").required);
    }

    @Test void regexKeyWithAlternationMatches() {
        class P { String email; String contactEmail; String phone; }
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("fields", new LinkedHashMap<>(Map.of("email|contactEmail", Map.of("required", true))));
        List<FormGen.Control> cs = FormGen.of(P.class).yaml(spec).visibleControls();
        assertTrue(find(cs, "email").required);
        assertTrue(find(cs, "contactEmail").required);
        assertFalse(find(cs, "phone").required);
    }
    @Test
    void independentInstancesAreSafe() throws Exception {
        int threads = 32, iterations = 5_000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            futures.add(pool.submit(() -> {
                for (int i = 0; i < iterations; i++) {
                    Map<String, Object> data = Map.of("email", "u@e.com", "amount", 1);
                    Map<String, Object> spec = YamlSpec.loadFromString(
                            "fields:\n  email: { label: Почта, required: true }\n");
                    String html = FormGen.ofData(data).yaml(spec).form("/save", "post");
                    if (!html.contains("name=\"email\"")) throw new AssertionError(html);
                }
                return null;
            }));
        }
        for (Future<?> f : futures) f.get();
        pool.shutdown();
    }
}
