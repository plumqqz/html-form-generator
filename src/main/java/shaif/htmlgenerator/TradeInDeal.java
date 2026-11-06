package shaif.htmlgenerator;
import shaif.htmlgenerator.htmlform.FieldType;
import shaif.htmlgenerator.htmlform.FormField;
import java.math.BigDecimal;
import java.time.LocalDate;

public class TradeInDeal {
    private Long id;
    private String dealId;
    private String orgName, inn, kpp, contactPerson, contactPhone, contactEmail;
    private String newCarVin;
    private String newCarMake;
    private String newCarModel;
    private Integer newCarYear;
    private String newCarColor;
    private BigDecimal newCarPrice;
    private String tradeInVin;
    @FormField(type = FieldType.SELECT, options = {
        "audi=Audi", "bmw=BMW", "mercedes=Mercedes", "toyota=Toyota", "volvo=Volvo"
    })
    private String tradeInMake;
    private String tradeInModel;
    private Integer tradeInYear;
    private Integer tradeInMileage;
    private TradeInCondition tradeInCondition;
    private BigDecimal loanAmount, interestRate;
    private Integer loanTerm;
    private String paymentMethod;
    private LocalDate paymentDate;
    private String notes;

    public String getOrgName() { return orgName; }
    public String getTradeInMake() { return tradeInMake; }
    public TradeInCondition getTradeInCondition() { return tradeInCondition; }

    public TradeInDeal setOrgName(String v) { orgName = v; return this; }
    public TradeInDeal setInn(String v) { inn = v; return this; }
    public TradeInDeal setKpp(String v) { kpp = v; return this; }
    public TradeInDeal setContactPerson(String v) { contactPerson = v; return this; }
    public TradeInDeal setContactPhone(String v) { contactPhone = v; return this; }
    public TradeInDeal setContactEmail(String v) { contactEmail = v; return this; }
    public TradeInDeal setNewCarVin(String v) { newCarVin = v; return this; }
    public TradeInDeal setNewCarMake(String v) { newCarMake = v; return this; }
    public TradeInDeal setNewCarModel(String v) { newCarModel = v; return this; }
    public TradeInDeal setNewCarYear(Integer v) { newCarYear = v; return this; }
    public TradeInDeal setNewCarColor(String v) { newCarColor = v; return this; }
    public TradeInDeal setNewCarPrice(BigDecimal v) { newCarPrice = v; return this; }
    public TradeInDeal setTradeInVin(String v) { tradeInVin = v; return this; }
    public TradeInDeal setTradeInMake(String v) { tradeInMake = v; return this; }
    public TradeInDeal setTradeInModel(String v) { tradeInModel = v; return this; }
    public TradeInDeal setTradeInYear(Integer v) { tradeInYear = v; return this; }
    public TradeInDeal setTradeInMileage(Integer v) { tradeInMileage = v; return this; }
    public TradeInDeal setTradeInCondition(TradeInCondition v) { tradeInCondition = v; return this; }
    public TradeInDeal setLoanAmount(BigDecimal v) { loanAmount = v; return this; }
    public TradeInDeal setLoanTerm(Integer v) { loanTerm = v; return this; }
    public TradeInDeal setInterestRate(BigDecimal v) { interestRate = v; return this; }
    public TradeInDeal setPaymentMethod(String v) { paymentMethod = v; return this; }
    public TradeInDeal setPaymentDate(LocalDate v) { paymentDate = v; return this; }
    public TradeInDeal setNotes(String v) { notes = v; return this; }
}
