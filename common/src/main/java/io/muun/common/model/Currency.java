package io.muun.common.model;

import io.muun.common.Optional;

import java.util.HashMap;
import java.util.Map;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.UnknownCurrencyException;

/**
 * Model of a fiat currency.
 */
public class Currency {

    public static final Map<String, Currency> CURRENCIES;

    static {
        CURRENCIES = new HashMap<String, Currency>();
        CURRENCIES.put("AED", new Currency("AED", "د.إ", "UAE Dirham"));
        CURRENCIES.put("AFN", new Currency("AFN", "؋", "Afghan Afghani"));
        CURRENCIES.put("ALL", new Currency("ALL", "LEK", "Albanian Lek"));
        CURRENCIES.put("AMD", new Currency("AMD", "Դրամ", "Armenian Dram"));
        CURRENCIES.put("ANG", new Currency("ANG", "ƒ", "Netherlands Antillean Guilder"));
        CURRENCIES.put("AOA", new Currency("AOA", "Kz", "Angolan Kwanza"));
        CURRENCIES.put("ARS", new Currency("ARS", "$", "Argentine Peso"));
        CURRENCIES.put("AUD", new Currency("AUD", "$", "Australian Dollar"));
        CURRENCIES.put("AWG", new Currency("AWG", "ƒ", "Aruban Florin"));
        CURRENCIES.put("AZN", new Currency("AZN", "ман", "Azerbaijani Manat"));
        CURRENCIES.put("BAM", new Currency("BAM", "KM", "Bosnia-Herzegovina Convertible Mark"));
        CURRENCIES.put("BBD", new Currency("BBD", "$", "Barbadian Dollar"));
        CURRENCIES.put("BGN", new Currency("BGN", "лв", "Bulgarian Lev"));
        CURRENCIES.put("BHD", new Currency("BHD", ".د.ب", "Bahraini Dinar"));
        CURRENCIES.put("BIF", new Currency("BIF", "FBu", "Burundian Franc"));
        CURRENCIES.put("BMD", new Currency("BMD", "$", "Bermudan Dollar"));
        CURRENCIES.put("BND", new Currency("BND", "$", "Brunei Dollar"));
        CURRENCIES.put("BRL", new Currency("BRL", "R$", "Brasilian Real"));
        CURRENCIES.put("BSD", new Currency("BSD", "$", "Bahamian Dollar"));
        CURRENCIES.put("BTC", new Currency("BTC", "", "Bitcoin"));
        CURRENCIES.put("BTN", new Currency("BTN", "Nu", "Bhutanese Ngultrum"));
        CURRENCIES.put("BWP", new Currency("BWP", "P", "Botswanan Pula"));
        CURRENCIES.put("BYR", new Currency("BYR", "p.", "Belarusian Ruble"));
        CURRENCIES.put("BZD", new Currency("BZD", "BZ$", "Belize Dollar"));
        CURRENCIES.put("CAD", new Currency("CAD", "$", "Canadian Dollar"));
        CURRENCIES.put("CDF", new Currency("CDF", "FC", "Congolese Franc"));
        CURRENCIES.put("CHF", new Currency("CHF", "CHF", "Swiss Franc"));
        CURRENCIES.put("CLF", new Currency("CLF", "UF", "Chilean Unit of Account (UF)"));
        CURRENCIES.put("CLP", new Currency("CLP", "$", "Chilean Peso"));
        CURRENCIES.put("CNY", new Currency("CNY", "¥", "Chinese Yuan"));
        CURRENCIES.put("COP", new Currency("COP", "$", "Colombian Peso"));
        CURRENCIES.put("CRC", new Currency("CRC", "₡", "Costa Rican Colón"));
        CURRENCIES.put("CVE", new Currency("CVE", "$", "Cape Verdean Escudo"));
        CURRENCIES.put("CZK", new Currency("CZK", "Kč", "Czech Koruna"));
        CURRENCIES.put("DJF", new Currency("DJF", "Fdj", "Djiboutian Franc"));
        CURRENCIES.put("DKK", new Currency("DKK", "kr", "Danish Krone"));
        CURRENCIES.put("DOP", new Currency("DOP", "RD$", "Dominican Peso"));
        CURRENCIES.put("DZD", new Currency("DZD", "دج", "Algerian Dinar"));
        // CURRENCIES.put("EEK", new Currency("EEK", "kr", "Estonian Kroon"));
        CURRENCIES.put("EGP", new Currency("EGP", "£", "Egyptian Pound"));
        CURRENCIES.put("ETB", new Currency("ETB", "Br", "Ethiopian Birr"));
        CURRENCIES.put("EUR", new Currency("EUR", "€", "Eurozone Euro"));
        CURRENCIES.put("FJD", new Currency("FJD", "$", "Fijian Dollar"));
        CURRENCIES.put("FKP", new Currency("FKP", "£", "Falkland Islands Pound"));
        CURRENCIES.put("GBP", new Currency("GBP", "£", "Pound Sterling"));
        CURRENCIES.put("GEL", new Currency("GEL", "ლ", "Georgian Lari"));
        CURRENCIES.put("GHS", new Currency("GHS", "¢", "Ghanaian Cedi"));
        CURRENCIES.put("GIP", new Currency("GIP", "£", "Gibraltar Pound"));
        CURRENCIES.put("GMD", new Currency("GMD", "D", "Gambian Dalasi"));
        CURRENCIES.put("GNF", new Currency("GNF", "FG", "Guinean Franc"));
        CURRENCIES.put("GTQ", new Currency("GTQ", "Q", "Guatemalan Quetzal"));
        CURRENCIES.put("GYD", new Currency("GYD", "$", "Guyanaese Dollar"));
        CURRENCIES.put("HKD", new Currency("HKD", "$", "Hong Kong Dollar"));
        CURRENCIES.put("HNL", new Currency("HNL", "L", "Honduran Lempira"));
        CURRENCIES.put("HRK", new Currency("HRK", "kn", "Croatian Kuna"));
        CURRENCIES.put("HTG", new Currency("HTG", "G", "Haitian Gourde"));
        CURRENCIES.put("HUF", new Currency("HUF", "Ft", "Hungarian Forint"));
        CURRENCIES.put("IDR", new Currency("IDR", "Rp", "Indonesian Rupiah"));
        CURRENCIES.put("ILS", new Currency("ILS", "₪", "Israeli Shekel"));
        CURRENCIES.put("INR", new Currency("INR", "₹", "Indian Rupee"));
        CURRENCIES.put("IQD", new Currency("IQD", "ع.د", "Iraqi Dinar"));
        CURRENCIES.put("ISK", new Currency("ISK", "kr", "Icelandic Króna"));
        CURRENCIES.put("JMD", new Currency("JMD", "J$", "Jamaican Dollar"));
        CURRENCIES.put("JOD", new Currency("JOD", "د.ا", "Jordanian Dinar"));
        CURRENCIES.put("JPY", new Currency("JPY", "¥", "Japanese Yen"));
        CURRENCIES.put("KES", new Currency("KES", "KSh", "Kenyan Shilling"));
        CURRENCIES.put("KHR", new Currency("KHR", "៛", "Cambodian Riel"));
        CURRENCIES.put("KMF", new Currency("KMF", "CF", "Comorian Franc"));
        CURRENCIES.put("KRW", new Currency("KRW", "₩", "South Korean Won"));
        CURRENCIES.put("KWD", new Currency("KWD", "د.ك", "Kuwaiti Dinar"));
        CURRENCIES.put("KYD", new Currency("KYD", "$", "Cayman Islands Dollar"));
        CURRENCIES.put("KZT", new Currency("KZT", "лв", "Kazakhstani Tenge"));
        CURRENCIES.put("LAK", new Currency("LAK", "₭", "Laotian Kip"));
        CURRENCIES.put("LBP", new Currency("LBP", "£", "Lebanese Pound"));
        CURRENCIES.put("LKR", new Currency("LKR", "₨", "Sri Lankan Rupee"));
        CURRENCIES.put("LRD", new Currency("LRD", "$", "Liberian Dollar"));
        CURRENCIES.put("LSL", new Currency("LSL", "L", "Lesotho Loti"));
        // CURRENCIES.put("LTL", new Currency("LTL", "Lt", "Lithuanian Litas"));
        // CURRENCIES.put("LVL", new Currency("LVL", "Ls", "Latvian Lats"));
        CURRENCIES.put("LYD", new Currency("LYD", "ل.د", "Libyan Dinar"));
        CURRENCIES.put("MAD", new Currency("MAD", "د.م", "Moroccan Dirham"));
        CURRENCIES.put("MDL", new Currency("MDL", "L", "Moldovan Leu"));
        CURRENCIES.put("MGA", new Currency("MGA", "Ar", "Malagasy Ariary"));
        CURRENCIES.put("MKD", new Currency("MKD", "ден", "Macedonian Denar"));
        CURRENCIES.put("MMK", new Currency("MMK", "K", "Myanma Kyat"));
        CURRENCIES.put("MNT", new Currency("MNT", "₮", "Mongolian Tugrik"));
        CURRENCIES.put("MOP", new Currency("MOP", "MOP$", "Macanese Pataca"));
        CURRENCIES.put("MRO", new Currency("MRO", "UM", "Mauritanian Ouguiya"));
        CURRENCIES.put("MUR", new Currency("MUR", "₨", "Mauritian Rupee"));
        CURRENCIES.put("MVR", new Currency("MVR", "MRf", "Maldivian Rufiyaa"));
        CURRENCIES.put("MWK", new Currency("MWK", "MK", "Malawian Kwacha"));
        CURRENCIES.put("MXN", new Currency("MXN", "$", "Mexican Peso"));
        CURRENCIES.put("MYR", new Currency("MYR", "RM", "Malaysian Ringgit"));
        CURRENCIES.put("MZN", new Currency("MZN", "MT", "Mozambican Metical"));
        CURRENCIES.put("NAD", new Currency("NAD", "MT", "Namibian Dollar"));
        CURRENCIES.put("NGN", new Currency("NGN", "₦", "Nigerian Naira"));
        CURRENCIES.put("NIO", new Currency("NIO", "C", "Nicaraguan Córdoba"));
        CURRENCIES.put("NOK", new Currency("NOK", "kr", "Norwegian Krone"));
        CURRENCIES.put("NPR", new Currency("NPR", "₨", "Nepalese Rupee"));
        CURRENCIES.put("NZD", new Currency("NZD", "$", "New Zealand Dollar"));
        CURRENCIES.put("OMR", new Currency("OMR", "﷼", "Omani Rial"));
        CURRENCIES.put("PAB", new Currency("PAB", "B/.", "Panamanian Balboa"));
        CURRENCIES.put("PEN", new Currency("PEN", "S/.", "Peruvian Nuevo Sol"));
        CURRENCIES.put("PGK", new Currency("PGK", "K", "Papua New Guinean Kina"));
        CURRENCIES.put("PHP", new Currency("PHP", "₱", "Philippine Peso"));
        CURRENCIES.put("PKR", new Currency("PKR", "₨", "Pakistani Rupee"));
        CURRENCIES.put("PLN", new Currency("PLN", "zł", "Polish Zloty"));
        CURRENCIES.put("PYG", new Currency("PYG", "Gs", "Paraguayan Guarani"));
        CURRENCIES.put("QAR", new Currency("QAR", "﷼", "Qatari Rial"));
        CURRENCIES.put("RON", new Currency("RON", "lei", "Romanian Leu"));
        CURRENCIES.put("RSD", new Currency("RSD", "Дин.", "Serbian Dinar"));
        CURRENCIES.put("RUB", new Currency("RUB", "ру", "Russian Ruble"));
        CURRENCIES.put("RWF", new Currency("RWF", "RF", "Rwandan Franc"));
        CURRENCIES.put("SAR", new Currency("SAR", "﷼", "Saudi Riyal"));
        CURRENCIES.put("SBD", new Currency("SBD", "$", "Solomon Islands Dollar"));
        CURRENCIES.put("SCR", new Currency("SCR", "₨", "Seychellois Rupee"));
        CURRENCIES.put("SEK", new Currency("SEK", "kr", "Swedish Krona"));
        CURRENCIES.put("SGD", new Currency("SGD", "$", "Singapore Dollar"));
        CURRENCIES.put("SHP", new Currency("SHP", "£", "Saint Helena Pound"));
        CURRENCIES.put("SLL", new Currency("SLL", "Le", "Sierra Leonean Leone"));
        CURRENCIES.put("SOS", new Currency("SOS", "S", "Somali Shilling"));
        CURRENCIES.put("SRD", new Currency("SRD", "$", "Surinamese Dollar"));
        CURRENCIES.put("STD", new Currency("STD", "Db", "São Tomé and Príncipe Dobra"));
        CURRENCIES.put("SVC", new Currency("SVC", "₡", "Salvadoran Colón"));
        CURRENCIES.put("SZL", new Currency("SZL", "L", "Swazi Lilangeni"));
        CURRENCIES.put("THB", new Currency("THB", "฿", "Thai Baht"));
        CURRENCIES.put("TJS", new Currency("TJS", "SM", "Tajikistani Somoni"));
        CURRENCIES.put("TMT", new Currency("TMT", "m", "Turkmenistani Manat"));
        CURRENCIES.put("TND", new Currency("TND", "د.ت", "Tunisian Dinar"));
        CURRENCIES.put("TOP", new Currency("TOP", "T$", "Tongan Paʻanga"));
        CURRENCIES.put("TRY", new Currency("TRY", "₤", "Turkish Lira"));
        CURRENCIES.put("TTD", new Currency("TTD", "TT$", "Trinidad and Tobago Dollar"));
        CURRENCIES.put("TWD", new Currency("TWD", "NT$", "New Taiwan Dollar"));
        CURRENCIES.put("TZS", new Currency("TZS", "x/y", "Tanzanian Shilling"));
        CURRENCIES.put("UAH", new Currency("UAH", "₴", "Ukrainian Hryvnia"));
        CURRENCIES.put("UGX", new Currency("UGX", "USh", "Ugandan Shilling"));
        CURRENCIES.put("USD", new Currency("USD", "$", "US Dollar"));
        CURRENCIES.put("UYU", new Currency("UYU", "$U", "Uruguayan Peso"));
        CURRENCIES.put("UZS", new Currency("UZS", "лв", "Uzbekistan Som"));
        CURRENCIES.put("VEF", new Currency("VEF", "Bs", "Venezuelan Bolívar Fuerte"));
        CURRENCIES.put("VND", new Currency("VND", "₫", "Vietnamese Dong"));
        CURRENCIES.put("VUV", new Currency("VUV", "Vt", "Vanuatu Vatu"));
        CURRENCIES.put("WST", new Currency("WST", "WS$", "Samoan Tala"));
        CURRENCIES.put("XAF", new Currency("XAF", "FCFA", "CFA Franc BEAC"));
        CURRENCIES.put("XAG", new Currency("XAG", "oz.", "Silver (troy ounce)"));
        CURRENCIES.put("XAU", new Currency("XAU", "oz.", "Gold (troy ounce)"));
        CURRENCIES.put("XCD", new Currency("XCD", "$", "East Caribbean Dollar"));
        CURRENCIES.put("XOF", new Currency("XOF", "CFA", "CFA Franc BCEAO"));
        CURRENCIES.put("XPF", new Currency("XPF", "F", "CFP Franc"));
        CURRENCIES.put("YER", new Currency("YER", "﷼", "Yemeni Rial"));
        CURRENCIES.put("ZAR", new Currency("ZAR", "R", "South African Rand"));
        CURRENCIES.put("ZMW", new Currency("ZMW", "ZK", "Zambian Kwacha"));
        CURRENCIES.put("ZWL", new Currency("ZWL", "Z$", "Zimbabwean Dollar"));
    }

    /**
     * Return a supported CurrencyUnit that matches the currency code.
     */
    public static Optional<CurrencyUnit> getUnit(String currencyCode) {
        try {
            if (CURRENCIES.containsKey(currencyCode)) {
                return Optional.of(Monetary.getCurrency(currencyCode));
            } else {
                return Optional.empty(); // not supported by us
            }

        } catch (UnknownCurrencyException ex) {
            return Optional.empty(); // not supported by the system
        }
    }

    /**
     * Returns the currency metadata for the given code, if known.
     */
    public static Optional<Currency> getInfo(String currencyCode) {
        return Optional.of(CURRENCIES.get(currencyCode));
    }

    private final String code;

    private final String symbol;

    private final String name;

    /**
     * Constructor.
     */
    public Currency(String code, String symbol, String name) {
        this.code = code;
        this.symbol = symbol;
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getName() {
        return name;
    }
}
