package shaif.htmlgenerator;

import shaif.htmlgenerator.htmlform.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

public class Demo {
    static class Db {
        final Locale loc; Db(Locale l) { this.loc = l; }
        boolean en() { return "en".equals(loc.getLanguage()); }
        List<Option> countries() { return Arrays.asList(
            new Option("RU", en() ? "Russia" : "Россия"),
            new Option("KZ", en() ? "Kazakhstan" : "Казахстан"),
            new Option("BY", en() ? "Belarus" : "Беларусь")); }
        List<Option> cities(String c) {
            if ("RU".equals(c)) return Arrays.asList(
                new Option("MSK", en() ? "Moscow" : "Москва"),
                new Option("SPB", en() ? "St. Petersburg" : "Санкт-Петербург"));
            if ("KZ".equals(c)) return Arrays.asList(
                new Option("ALA", en() ? "Almaty" : "Алматы"),
                new Option("AST", en() ? "Astana" : "Астана"));
            return Collections.emptyList(); }
        List<Option> roles() { return Arrays.asList(
            new Option("ADMIN", en() ? "Administrator" : "Администратор"),
            new Option("USER",  en() ? "User" : "Пользователь"),
            new Option("GUEST", en() ? "Guest" : "Гость")); }
        List<Option> departments() {
            String tTech = en() ? "Technical" : "Технические";
            String tArch = en() ? "Archive" : "Архив";
            return Arrays.asList(
                new Option("IT", "IT", tTech),
                new Option("QA", "QA", tTech),
                new Option("OLD", en() ? "Old" : "Старый отдел", tArch, true)); }
    }

    public static void main(String[] a) throws Exception {
        Path out = Paths.get("target/demo");
        Files.createDirectories(out);
        for (String lang : new String[]{"ru", "en"}) {
            Locale loc = Locale.forLanguageTag(lang);
            Db db = new Db(loc);
            User u = sampleUser();
            Map<String, Object> spec = YamlSpec.loadFromClasspath(User.class, loc);

            FormGen base = FormGen.of(u).yaml(spec)
                .options("role", db.roles()).options("country", db.countries())
                .options("city", ctx -> { User x = ctx.owner(User.class);
                    return x == null || x.getCountry() == null ? Collections.emptyList() : db.cities(x.getCountry()); })
                .options("department", db.departments());

            write(out, "form-vertical-" + lang + ".html", "Vertical (" + lang + ")",
                  base.form(Layout.VERTICAL, "/save", "post", "Save"));
            write(out, "form-horizontal-" + lang + ".html", "Horizontal (" + lang + ")",
                  base.form(Layout.HORIZONTAL, "/save", "post", "Save"));
            write(out, "form-inline-" + lang + ".html", "Inline (" + lang + ")",
                  base.form(Layout.INLINE, "/save", "post", "Save"));

            Map<String, List<String>> fieldErrors = new LinkedHashMap<>();
            fieldErrors.put("email", List.of(
                lang.equals("ru") ? "Обязательное поле" : "Required",
                lang.equals("ru") ? "Неверный формат" : "Invalid format"));
            fieldErrors.put("phone", List.of(lang.equals("ru") ? "Слишком короткий" : "Too short"));

            Map<String, List<String>> fieldWarnings = new LinkedHashMap<>();
            fieldWarnings.put("phone", List.of(lang.equals("ru") ? "Номер скоро изменится" : "Number will change soon"));

            List<String> globalErrors = List.of(lang.equals("ru") ? "Пароли не совпадают" : "Passwords do not match");
            List<String> globalWarnings = List.of(lang.equals("ru") ? "Данные сохранены час назад" : "Saved an hour ago");

            String withErrors = base
                .errors(fieldErrors).warnings(fieldWarnings)
                .errors(globalErrors).warnings(globalWarnings)
                .csrf("tok-" + UUID.randomUUID().toString().substring(0, 8))
                .readonly("birthDate").readonly("firstName")
                .disabled("middleName").disabled("active")
                .formId("user-edit")
                .formAttr("novalidate", null)
                .formAttr("autocomplete", "off")
                .form(Layout.VERTICAL, "/save", "post", "Save");
            write(out, "form-with-errors-" + lang + ".html", "With errors / csrf / readonly (" + lang + ")", withErrors);

            List<User> users = Arrays.asList(u, secondUser());
            write(out, "table-" + lang + ".html", "Table (" + lang + ")",
                  FormGen.of(User.class).yaml(spec).table(users)
                      .caption(lang.equals("ru") ? "Пользователи системы" : "System users")
                      .footer(lang.equals("ru") ? "Итого: 2" : "Total: 2", "", "", "")
                      .rowClass(x -> ((User) x).getId() == 1L ? "row-active" : "row-inactive")
                      .cellClass("email", "text-muted")
                      .cellClass("firstName", "text-bold")
                      .link("/users/{id}").link("email", "mailto:{_}")
                      .action(lang.equals("ru") ? "Изменить" : "Edit",
                              x -> "/users/" + ((User) x).getId() + "/edit")
                      .empty(lang.equals("ru") ? "Пользователи не найдены" : "No users")
                      .html());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("number", "PP-2024/001"); data.put("amount", "125000.00");
        data.put("currency", "RUB"); data.put("purpose", "Оплата по счёту № 42");
        data.put("status", "pending");
        Map<String, Object> paySpec = YamlSpec.loadFromClasspath(Payment.class, Locale.forLanguageTag("ru"));
        write(out, "form-payment-map.html", "Payment (Map)",
              FormGen.ofData(data).yaml(paySpec).csrf("tok-123").form("/payments/save", "post"));

        // Order Demo (Multi-extends)
        Map<String, Object> orderData = new LinkedHashMap<>();
        orderData.put("number",   "ORD-2024/001");
        orderData.put("firstName","Иван");
        orderData.put("lastName", "Петров");
        orderData.put("email",    "petrov@example.com");
        orderData.put("phone",    "+7 (999) 111-22-33");
        orderData.put("amount",   "125000.00");
        orderData.put("currency", "RUB");
        orderData.put("status",   "new");
        Map<String, Object> orderSpec = YamlSpec.loadFromClasspath(Order.class, Locale.forLanguageTag("ru"));
        write(out, "form-order-ru.html", "Order (extends: [Person, Finance])",
              FormGen.ofData(orderData).yaml(orderSpec).csrf("tok-order-123").form("/orders/save", "post"));

        // Trade-In Demo (Custom Rendering)
        TradeInDeal deal = new TradeInDeal()
            .setOrgName("ООО Ромашка").setInn("7701234567")
            .setContactPerson("Иванов Иван")
            .setNewCarMake("audi").setNewCarModel("a6")
            .setTradeInMake("audi").setTradeInModel("a4")
            .setTradeInCondition(TradeInCondition.GOOD);
        Map<String, List<String>> dealErrors = new LinkedHashMap<>();
        dealErrors.put("tradeInVin", List.of("VIN не распознан (проверьте символы)"));
        write(out, "form-tradein-ru.html", "Trade-In Deal (Custom Rendering)",
              new TradeInFormRenderer().render(deal, dealErrors, "tok-tradein-123"));

        // Regex matching demo
        Map<String, Object> regexSpec = new LinkedHashMap<>();
        Map<String, Object> regexFields = new LinkedHashMap<>();
        regexFields.put(".*Id$",  Map.of("type", "HIDDEN"));
        regexFields.put("userId", Map.of("type", "TEXT"));
        regexFields.put(".*Email", Map.of("required", true));
        regexSpec.put("fields", regexFields);
        write(out, "form-regex-demo.html", "Regex keys (userId visible, orderId hidden)",
              FormGen.of(sampleUser()).yaml(regexSpec).form("/save", "post"));
    }

    private static User sampleUser() {
        return new User().setId(1L).setFirstName("Иван").setLastName("Иванов").setMiddleName("Иванович")
            .setBirthDate(LocalDate.of(1990, 5, 17)).setEmail("ivan@example.com").setPhone("+7 (999) 123-45-67")
            .setCountry("RU").setCity("MSK").setRole("USER").setDepartment("IT")
            .setGender(User.Gender.M).setPermissions(Arrays.asList("read", "write")).setActive(true);
    }
    private static User secondUser() {
        return new User().setId(2L).setFirstName("Мария").setLastName("Петрова")
            .setBirthDate(LocalDate.of(1985, 11, 3)).setEmail("maria@example.com")
            .setCountry("KZ").setCity("ALA").setRole("ADMIN").setDepartment("QA")
            .setGender(User.Gender.F).setPermissions(Collections.singletonList("admin")).setActive(false);
    }

    private static void write(Path dir, String name, String title, String body) throws Exception {
        String html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"/><title>" + title
            + "</title><style>"
            + "body{font-family:sans-serif;padding:2rem;max-width:1100px;margin:auto}"
            + "table{border-collapse:collapse;width:100%}th,td{border:1px solid #ccc;padding:.4rem .6rem;text-align:left}th{background:#f5f5f5}"
            + "caption{font-weight:600;padding:.5rem;background:#eee;text-align:left}"
            + "tfoot td{background:#fafafa;font-weight:600}"
            + ".field{margin-bottom:.9rem}.field label{display:block;margin-bottom:.2rem;font-weight:600}"
            + ".field.row{display:flex;gap:.5rem;align-items:flex-start}.field.row .col-label{flex:0 0 220px;font-weight:600}.field.row .col-control{flex:1}"
            + ".sr-only{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0,0,0,0)}"
            + ".form-inline .field{margin-right:1rem}"
            + ".required{color:#c00}.row{display:flex;gap:.5rem;margin-bottom:.9rem}.row .lbl{flex:0 0 220px;font-weight:600}.row .ctl{flex:1}"
            + ".error{color:#c00;display:block;font-size:.875em;margin-top:.15rem}"
            + ".warning{color:#a60;display:block;font-size:.875em;margin-top:.15rem}"
            + ".has-error>label{color:#c00}.has-error input,.has-error select,.has-error textarea{border-color:#c00}"
            + ".has-warning input,.has-warning select,.has-warning textarea{border-color:#e6a300}"
            + ".form-errors{background:#fee;border:1px solid #c00;padding:.5rem .75rem;margin-bottom:1rem;border-radius:4px}"
            + ".form-warnings{background:#fff8e1;border:1px solid #e6a300;padding:.5rem .75rem;margin-bottom:1rem;border-radius:4px}"
            + ".form-errors .error,.form-warnings .warning{display:block;margin:0}"
            + ".form-inline .error,.form-inline .warning{display:inline;margin-left:.25rem}"
            + ".row-active{background:#eef7ff}.row-inactive{background:#fafafa}"
            + ".text-muted{color:#888}.text-bold{font-weight:600}"
            + "input[readonly]{background:#f4f4f4}"
            + "input[disabled],select[disabled],textarea[disabled]{background:#eee;color:#888;cursor:not-allowed}"
            + ".tradein-form { max-width: 1000px; margin: auto; font-family: sans-serif; }"
            + ".tradein-form .section { border: 1px solid #ccc; border-radius: 6px; padding: 1rem 1.25rem; margin-bottom: 1.5rem; }"
            + ".tradein-form .section legend { font-weight: 600; padding: 0 .5rem; }"
            + ".tradein-form .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 1rem; }"
            + ".tradein-form .cell { display: flex; flex-direction: column; }"
            + ".tradein-form .cell label { font-weight: 600; margin-bottom: .2rem; }"
            + ".tradein-form .cell.has-error input, .tradein-form .cell.has-error select, .tradein-form .cell.has-error textarea { border-color: #c00; }"
            + ".tradein-form .cell.has-warning input, .tradein-form .cell.has-warning select, .tradein-form .cell.has-warning textarea { border-color: #e6a300; }"
            + ".tradein-form .error { color: #c00; font-size: .875em; margin-top: .15rem; }"
            + ".tradein-form .warning { color: #a60; font-size: .875em; margin-top: .15rem; }"
            + ".tradein-form .global-errors { background: #fee; border: 1px solid #c00; border-radius: 4px; padding: .5rem 1rem; margin-bottom: 1rem; }"
            + ".tradein-form .actions { text-align: right; }"
            + "</style></head><body><h1>" + title + "</h1>\n" + body + "\n</body></html>";
        Files.write(dir.resolve(name), html.getBytes(StandardCharsets.UTF_8));
        System.out.println("✓ target/demo/" + name);
    }
}
