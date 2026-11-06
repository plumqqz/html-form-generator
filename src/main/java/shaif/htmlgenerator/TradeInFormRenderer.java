package shaif.htmlgenerator;

import shaif.htmlgenerator.htmlform.FormGen;
import shaif.htmlgenerator.htmlform.Option;
import shaif.htmlgenerator.htmlform.YamlSpec;
import java.util.*;

public class TradeInFormRenderer {
    public String render(TradeInDeal deal, Map<String, List<String>> errors, String csrfToken) {
        FormGen g = FormGen.of(deal)
            .yaml(YamlSpec.loadFromClasspath(TradeInDeal.class, Locale.forLanguageTag("ru")))
            .csrf(csrfToken)
            .options("newCarModel", Arrays.asList(
                new Option("a4", "A4"), new Option("a6", "A6"),
                new Option("x5", "X5"), new Option("x6", "X6"),
                new Option("e", "E-Class"), new Option("camry", "Camry")))
            .options("tradeInModel", ctx -> {
                TradeInDeal d = ctx.owner(TradeInDeal.class);
                if (d == null || d.getTradeInMake() == null) return Collections.emptyList();
                return modelsByMake(d.getTradeInMake());
            })
            .options("paymentMethod", "cash=Наличные", "bank=Безналичный", "loan=Рассрочка")
            .errors(errors);

        Map<String, FormGen.Control> byName = new HashMap<>();
        for (FormGen.Control c : g.visibleControls()) byName.put(c.name, c);

        FormGen.Control photo = FormGen.Control.of(
            "tradeInPhoto", "Фотографии (до 10 шт.)",
            "<input type=\"file\" name=\"tradeInPhoto\" multiple accept=\"image/*\" class=\"file-input\"/>");

        StringBuilder sb = new StringBuilder();
        sb.append("<form method=\"post\" action=\"/tradein/save\" enctype=\"multipart/form-data\" class=\"tradein-form\">\n");
        for (FormGen.Control h : g.hiddenControls()) sb.append(h.html()).append("\n");

        sb.append(section("Организация-покупатель", byName.get("orgName"), byName.get("inn"), byName.get("kpp"), byName.get("contactPerson"), byName.get("contactPhone"), byName.get("contactEmail")));
        sb.append(section("Новый автомобиль", byName.get("newCarVin"), byName.get("newCarMake"), byName.get("newCarModel"), byName.get("newCarYear"), byName.get("newCarColor"), byName.get("newCarPrice")));
        sb.append(section("Сдаваемый автомобиль (trade-in)", byName.get("tradeInVin"), byName.get("tradeInMake"), byName.get("tradeInModel"), byName.get("tradeInYear"), byName.get("tradeInMileage"), byName.get("tradeInCondition"), photo));
        sb.append(section("Финансовые условия", byName.get("loanAmount"), byName.get("loanTerm"), byName.get("interestRate"), byName.get("paymentMethod"), byName.get("paymentDate")));
        sb.append(section("Примечания", byName.get("notes")));

        if (!g.globalErrors().isEmpty()) {
            sb.append("<div class=\"global-errors\">\n");
            for (String e : g.globalErrors()) sb.append("  <span class=\"error\">").append(FormGen.esc(e)).append("</span>\n");
            sb.append("</div>\n");
        }

        sb.append("<div class=\"actions\"><button type=\"submit\">Отправить заявку</button></div>\n</form>\n");
        return sb.toString();
    }

    private static List<Option> modelsByMake(String make) {
        switch (make) {
            case "audi":     return Arrays.asList(new Option("a4", "A4"), new Option("a6", "A6"));
            case "bmw":      return Arrays.asList(new Option("x5", "X5"), new Option("x6", "X6"));
            case "mercedes": return Collections.singletonList(new Option("e", "E-Class"));
            case "toyota":   return Collections.singletonList(new Option("camry", "Camry"));
            case "volvo":    return Arrays.asList(new Option("xc60", "XC60"), new Option("xc90", "XC90"));
            default:         return Collections.emptyList();
        }
    }

    private static String section(String title, FormGen.Control... controls) {
        StringBuilder sb = new StringBuilder();
        sb.append("<fieldset class=\"section\">\n  <legend>").append(FormGen.esc(title)).append("</legend>\n  <div class=\"grid\">\n");
        for (FormGen.Control c : controls) {
            if (c == null) continue;
            sb.append("    <div class=\"cell");
            if (c.hasErrors()) sb.append(" has-error");
            else if (c.hasWarnings()) sb.append(" has-warning");
            sb.append("\">\n      <label for=\"f-").append(FormGen.esc(c.name)).append("\">").append(FormGen.esc(c.label));
            if (c.required) sb.append(" <span class=\"req\">*</span>");
            sb.append("</label>\n      ").append(c.html()).append("\n");
            for (String e : c.errors()) sb.append("      <span class=\"error\">").append(FormGen.esc(e)).append("</span>\n");
            for (String w : c.warnings()) sb.append("      <span class=\"warning\">").append(FormGen.esc(w)).append("</span>\n");
            sb.append("    </div>\n");
        }
        sb.append("  </div>\n</fieldset>\n");
        return sb.toString();
    }
}
