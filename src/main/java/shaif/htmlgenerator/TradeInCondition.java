package shaif.htmlgenerator;
import shaif.htmlgenerator.htmlform.FormField;
public enum TradeInCondition {
    @FormField(label = "Отличное")              EXCELLENT,
    @FormField(label = "Хорошее")               GOOD,
    @FormField(label = "Удовлетворительное")    FAIR,
    @FormField(label = "Требует ремонта")       POOR
}
