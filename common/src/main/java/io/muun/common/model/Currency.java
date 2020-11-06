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
@SuppressWarnings("checkstyle:AvoidEscapedUnicodeCharacters")
public class Currency {

    public static final Map<String, Currency> CURRENCIES;

    public static final Map<String, Currency> NOT_SUPPORTED_BY_HOUSTON = new HashMap<>();

    static {
        CURRENCIES = new HashMap<>();

        load(new Currency("AED", "د.إ", "UAE Dirham", "\uD83C\uDDE6\uD83C\uDDEA"));
        load(new Currency("AFN", "؋", "Afghan Afghani", "\uD83C\uDDE6\uD83C\uDDEB"));
        load(new Currency("ALL", "LEK", "Albanian Lek", "\uD83C\uDDE6\uD83C\uDDF1"));
        load(new Currency("AMD", "Դրամ", "Armenian Dram", "\uD83C\uDDE6\uD83C\uDDF2"));
        load(new Currency("ANG", "ƒ", "Netherlands Antillean Guilder", "\uD83C\uDDE8\uD83C\uDDFC"));
        load(new Currency("AOA", "Kz", "Angolan Kwanza", "\uD83C\uDDE6\uD83C\uDDF4"));
        load(new Currency("ARS", "$", "Argentine Peso", "\uD83C\uDDE6\uD83C\uDDF7"));
        load(new Currency("AUD", "$", "Australian Dollar", "\uD83C\uDDE6\uD83C\uDDFA"));
        load(new Currency("AWG", "ƒ", "Aruban Florin", "\uD83C\uDDE6\uD83C\uDDFC"));
        load(new Currency("AZN", "ман", "Azerbaijani Manat", "\uD83C\uDDE6\uD83C\uDDFF"));
        load(
                new Currency(
                        "BAM",
                        "KM",
                        "Bosnia-Herzegovina Convertible Mark",
                        "\uD83C\uDDE7\uD83C\uDDE6"
                )
        );
        load(new Currency("BBD", "$", "Barbadian Dollar", "\uD83C\uDDE7\uD83C\uDDE7"));
        load(new Currency("BDT", "৳", "Bangladeshi Taka", "\uD83C\uDDE7\uD83C\uDDE9"));
        load(new Currency("BGN", "лв", "Bulgarian Lev", "\uD83C\uDDE7\uD83C\uDDEC"));
        load(new Currency("BHD", ".د.ب", "Bahraini Dinar", "\uD83C\uDDE7\uD83C\uDDED"));
        load(new Currency("BIF", "FBu", "Burundian Franc", "\uD83C\uDDE7\uD83C\uDDEE"));
        load(new Currency("BMD", "$", "Bermudan Dollar", "\uD83C\uDDE7\uD83C\uDDF2"));
        load(new Currency("BND", "$", "Brunei Dollar", "\uD83C\uDDE7\uD83C\uDDF3"));
        load(new Currency("BOB", "Bs", "Bolivian Boliviano", "\uD83C\uDDE7\uD83C\uDDF4"));
        load(new Currency("BRL", "R$", "Brasilian Real", "\uD83C\uDDE7\uD83C\uDDF7"));
        load(new Currency("BSD", "$", "Bahamian Dollar", "\uD83C\uDDE7\uD83C\uDDF8"));
        load(new Currency("BTC", "", "Bitcoin", null));
        load(new Currency("BTN", "Nu", "Bhutanese Ngultrum", "\uD83C\uDDE7\uD83C\uDDF9"));
        load(new Currency("BWP", "P", "Botswanan Pula", "\uD83C\uDDE7\uD83C\uDDFC"));
        load(new Currency("BYN", "Br", "Belarusian Ruble", "\uD83C\uDDE7\uD83C\uDDFE"));
        load(new Currency("BZD", "BZ$", "Belize Dollar", "\uD83C\uDDE7\uD83C\uDDFF"));
        load(new Currency("CAD", "$", "Canadian Dollar", "\uD83C\uDDE8\uD83C\uDDE6"));
        load(new Currency("CDF", "FC", "Congolese Franc", "\uD83C\uDDE8\uD83C\uDDE9"));
        load(new Currency("CHF", "CHF", "Swiss Franc", "\uD83C\uDDE8\uD83C\uDDED"));
        load(new Currency("CLF", "UF", "Chilean Unit of Account (UF)", "\uD83C\uDDE8\uD83C\uDDF1"));
        load(new Currency("CLP", "$", "Chilean Peso", "\uD83C\uDDE8\uD83C\uDDF1"));
        load(new Currency("CNY", "¥", "Chinese Yuan", "\uD83C\uDDE8\uD83C\uDDF3"));
        load(new Currency("COP", "$", "Colombian Peso", "\uD83C\uDDE8\uD83C\uDDF4"));
        load(new Currency("CRC", "₡", "Costa Rican Colón", "\uD83C\uDDE8\uD83C\uDDF7"));
        load(new Currency("CUP", "$", "Cuban Peso", "\uD83C\uDDE8\uD83C\uDDFA"));
        load(new Currency("CVE", "$", "Cape Verdean Escudo", "\uD83C\uDDE8\uD83C\uDDFB"));
        load(new Currency("CZK", "Kč", "Czech Koruna", "\uD83C\uDDE8\uD83C\uDDFF"));
        load(new Currency("DJF", "Fdj", "Djiboutian Franc", "\uD83C\uDDE9\uD83C\uDDEF"));
        load(new Currency("DKK", "kr", "Danish Krone", "\uD83C\uDDE9\uD83C\uDDF0"));
        load(new Currency("DOP", "RD$", "Dominican Peso", "\uD83C\uDDE9\uD83C\uDDF4"));
        load(new Currency("DZD", "دج", "Algerian Dinar", "\uD83C\uDDE9\uD83C\uDDF4"));
        // load(new Currency("EEK", "kr", "Estonian Kroon"));
        load(new Currency("EGP", "£", "Egyptian Pound", "\uD83C\uDDEA\uD83C\uDDEC"));
        load(new Currency("ETB", "Br", "Ethiopian Birr", "\uD83C\uDDEA\uD83C\uDDF9"));
        load(new Currency("EUR", "€", "Eurozone Euro", "\uD83C\uDDEA\uD83C\uDDFA"));
        load(new Currency("FJD", "$", "Fijian Dollar", "\uD83C\uDDEB\uD83C\uDDEF"));
        load(new Currency("FKP", "£", "Falkland Islands Pound", "\uD83C\uDDEB\uD83C\uDDF0"));
        load(new Currency("GBP", "£", "Pound Sterling", "\uD83C\uDDEC\uD83C\uDDE7"));
        load(new Currency("GEL", "ლ", "Georgian Lari", "\uD83C\uDDEC\uD83C\uDDEA"));
        load(new Currency("GHS", "¢", "Ghanaian Cedi", "\uD83C\uDDEC\uD83C\uDDED"));
        load(new Currency("GIP", "£", "Gibraltar Pound", "\uD83C\uDDEC\uD83C\uDDEE"));
        load(new Currency("GMD", "D", "Gambian Dalasi", "\uD83C\uDDEC\uD83C\uDDF2"));
        load(new Currency("GNF", "FG", "Guinean Franc", "\uD83C\uDDEC\uD83C\uDDF3"));
        load(new Currency("GTQ", "Q", "Guatemalan Quetzal", "\uD83C\uDDEC\uD83C\uDDF9"));
        load(new Currency("GYD", "$", "Guyanaese Dollar", "\uD83C\uDDEC\uD83C\uDDFE"));
        load(new Currency("HKD", "$", "Hong Kong Dollar", "\uD83C\uDDED\uD83C\uDDF0"));
        load(new Currency("HNL", "L", "Honduran Lempira", "\uD83C\uDDED\uD83C\uDDF3"));
        load(new Currency("HRK", "kn", "Croatian Kuna", "\uD83C\uDDED\uD83C\uDDF7"));
        load(new Currency("HTG", "G", "Haitian Gourde", "\uD83C\uDDED\uD83C\uDDF9"));
        load(new Currency("HUF", "Ft", "Hungarian Forint", "\uD83C\uDDED\uD83C\uDDFA"));
        load(new Currency("IDR", "Rp", "Indonesian Rupiah", "\uD83C\uDDEE\uD83C\uDDE9"));
        load(new Currency("ILS", "₪", "Israeli Shekel", "\uD83C\uDDEE\uD83C\uDDF1"));
        load(new Currency("INR", "₹", "Indian Rupee", "\uD83C\uDDEE\uD83C\uDDF3"));
        load(new Currency("IQD", "ع.د", "Iraqi Dinar", "\uD83C\uDDEE\uD83C\uDDF6"));
        load(new Currency("IRR", "﷼", "Iranian Rial", "\uD83C\uDDEE\uD83C\uDDF7"));
        load(new Currency("ISK", "kr", "Icelandic Króna", "\uD83C\uDDEE\uD83C\uDDF8"));
        load(new Currency("JMD", "J$", "Jamaican Dollar", "\uD83C\uDDEF\uD83C\uDDF2"));
        load(new Currency("JOD", "د.ا", "Jordanian Dinar", "\uD83C\uDDEF\uD83C\uDDF4"));
        load(new Currency("JPY", "¥", "Japanese Yen", "\uD83C\uDDEF\uD83C\uDDF5"));
        load(new Currency("KES", "KSh", "Kenyan Shilling", "\uD83C\uDDF0\uD83C\uDDEA"));
        load(new Currency("KGS", "Лв", "Kyrgystani Som", "\uD83C\uDDF0\uD83C\uDDEC"));
        load(new Currency("KHR", "៛", "Cambodian Riel", "\uD83C\uDDF0\uD83C\uDDED"));
        load(new Currency("KMF", "CF", "Comorian Franc", "\uD83C\uDDF0\uD83C\uDDF2"));
        load(new Currency("KPW", "₩", "North Korean Won", "\uD83C\uDDF0\uD83C\uDDF5"));
        load(new Currency("KRW", "₩", "South Korean Won", "\uD83C\uDDF0\uD83C\uDDF7"));
        load(new Currency("KWD", "د.ك", "Kuwaiti Dinar", "\uD83C\uDDF0\uD83C\uDDFC"));
        load(new Currency("KYD", "$", "Cayman Islands Dollar", "\uD83C\uDDF0\uD83C\uDDFE"));
        load(new Currency("KZT", "лв", "Kazakhstani Tenge", "\uD83C\uDDF0\uD83C\uDDFF"));
        load(new Currency("LAK", "₭", "Laotian Kip", "\uD83C\uDDF1\uD83C\uDDE6"));
        load(new Currency("LBP", "£", "Lebanese Pound", "\uD83C\uDDF1\uD83C\uDDE7"));
        load(new Currency("LKR", "₨", "Sri Lankan Rupee", "\uD83C\uDDF1\uD83C\uDDF0"));
        load(new Currency("LRD", "$", "Liberian Dollar", "\uD83C\uDDF1\uD83C\uDDF7"));
        load(new Currency("LSL", "L", "Lesotho Loti", "\uD83C\uDDF1\uD83C\uDDF8"));
        // load(new Currency("LTL", "Lt", "Lithuanian Litas"));
        // load(new Currency("LVL", "Ls", "Latvian Lats"));
        load(new Currency("LYD", "ل.د", "Libyan Dinar", "\uD83C\uDDF1\uD83C\uDDFE"));
        load(new Currency("MAD", "د.م", "Moroccan Dirham", "\uD83C\uDDF2\uD83C\uDDE6"));
        load(new Currency("MDL", "L", "Moldovan Leu", "\uD83C\uDDF2\uD83C\uDDE9"));
        load(new Currency("MGA", "Ar", "Malagasy Ariary", "\uD83C\uDDF2\uD83C\uDDEC"));
        load(new Currency("MKD", "ден", "Macedonian Denar", "\uD83C\uDDF2\uD83C\uDDF0"));
        load(new Currency("MMK", "K", "Myanma Kyat", "\uD83C\uDDF2\uD83C\uDDF2"));
        load(new Currency("MNT", "₮", "Mongolian Tugrik", "\uD83C\uDDF2\uD83C\uDDF3"));
        load(new Currency("MOP", "MOP$", "Macanese Pataca", "\uD83C\uDDF2\uD83C\uDDF4"));
        load(new Currency("MUR", "₨", "Mauritian Rupee", "\uD83C\uDDF2\uD83C\uDDFA"));
        load(new Currency("MVR", "MRf", "Maldivian Rufiyaa", "\uD83C\uDDF2\uD83C\uDDFB"));
        load(new Currency("MWK", "MK", "Malawian Kwacha", "\uD83C\uDDF2\uD83C\uDDFC"));
        load(new Currency("MXN", "$", "Mexican Peso", "\uD83C\uDDF2\uD83C\uDDFD"));
        load(new Currency("MYR", "RM", "Malaysian Ringgit", "\uD83C\uDDF2\uD83C\uDDFE"));
        load(new Currency("MZN", "MT", "Mozambican Metical", "\uD83C\uDDF2\uD83C\uDDFF"));
        load(new Currency("NAD", "MT", "Namibian Dollar", "\uD83C\uDDF3\uD83C\uDDE6"));
        load(new Currency("NGN", "₦", "Nigerian Naira", "\uD83C\uDDF3\uD83C\uDDEC"));
        load(new Currency("NIO", "C", "Nicaraguan Córdoba", "\uD83C\uDDF3\uD83C\uDDEE"));
        load(new Currency("NOK", "kr", "Norwegian Krone", "\uD83C\uDDF3\uD83C\uDDF4"));
        load(new Currency("NPR", "₨", "Nepalese Rupee", "\uD83C\uDDF3\uD83C\uDDF5"));
        load(new Currency("NZD", "$", "New Zealand Dollar", "\uD83C\uDDF3\uD83C\uDDFF"));
        load(new Currency("OMR", "﷼", "Omani Rial", "\uD83C\uDDF4\uD83C\uDDF2"));
        load(new Currency("PAB", "B/.", "Panamanian Balboa", "\uD83C\uDDF5\uD83C\uDDE6"));
        load(new Currency("PEN", "S/.", "Peruvian Nuevo Sol", "\uD83C\uDDF5\uD83C\uDDEA"));
        load(new Currency("PGK", "K", "Papua New Guinean Kina", "\uD83C\uDDF5\uD83C\uDDEC"));
        load(new Currency("PHP", "₱", "Philippine Peso", "\uD83C\uDDF5\uD83C\uDDED"));
        load(new Currency("PKR", "₨", "Pakistani Rupee", "\uD83C\uDDF5\uD83C\uDDF0"));
        load(new Currency("PLN", "zł", "Polish Zloty", "\uD83C\uDDF5\uD83C\uDDF1"));
        load(new Currency("PYG", "Gs", "Paraguayan Guarani", "\uD83C\uDDF5\uD83C\uDDFE"));
        load(new Currency("QAR", "﷼", "Qatari Rial", "\uD83C\uDDF6\uD83C\uDDE6"));
        load(new Currency("RON", "lei", "Romanian Leu", "\uD83C\uDDF7\uD83C\uDDF4"));
        load(new Currency("RSD", "Дин.", "Serbian Dinar", "\uD83C\uDDF7\uD83C\uDDF8"));
        load(new Currency("RUB", "ру", "Russian Ruble", "\uD83C\uDDF7\uD83C\uDDFA"));
        load(new Currency("RWF", "RF", "Rwandan Franc", "\uD83C\uDDF7\uD83C\uDDFC"));
        load(new Currency("SAR", "﷼", "Saudi Riyal", "\uD83C\uDDF8\uD83C\uDDE6"));
        load(new Currency("SBD", "$", "Solomon Islands Dollar", "\uD83C\uDDF8\uD83C\uDDE7"));
        load(new Currency("SCR", "₨", "Seychellois Rupee", "\uD83C\uDDF8\uD83C\uDDE8"));
        load(new Currency("SEK", "kr", "Swedish Krona", "\uD83C\uDDF8\uD83C\uDDEA"));
        load(new Currency("SGD", "$", "Singapore Dollar", "\uD83C\uDDF8\uD83C\uDDEC"));
        load(new Currency("SHP", "£", "Saint Helena Pound", "\uD83C\uDDF8\uD83C\uDDED"));
        load(new Currency("SLL", "Le", "Sierra Leonean Leone", "\uD83C\uDDF8\uD83C\uDDF1"));
        load(new Currency("SOS", "S", "Somali Shilling", "\uD83C\uDDF8\uD83C\uDDF4"));
        load(new Currency("SRD", "$", "Surinamese Dollar", "\uD83C\uDDF8\uD83C\uDDF7"));
        load(new Currency("SVC", "₡", "Salvadoran Colón", "\uD83C\uDDF8\uD83C\uDDFB"));
        load(new Currency("SYP", "LS", "Syrian Pound", "\uD83C\uDDF8\uD83C\uDDFE"));
        load(new Currency("SZL", "L", "Swazi Lilangeni", "\uD83C\uDDF8\uD83C\uDDFF"));
        load(new Currency("THB", "฿", "Thai Baht", "\uD83C\uDDF9\uD83C\uDDED"));
        load(new Currency("TJS", "SM", "Tajikistani Somoni", "\uD83C\uDDF9\uD83C\uDDEF"));
        load(new Currency("TMT", "m", "Turkmenistani Manat", "\uD83C\uDDF9\uD83C\uDDF2"));
        load(new Currency("TND", "د.ت", "Tunisian Dinar", "\uD83C\uDDF9\uD83C\uDDF3"));
        load(new Currency("TOP", "T$", "Tongan Paʻanga", "\uD83C\uDDF9\uD83C\uDDF4"));
        load(new Currency("TRY", "₤", "Turkish Lira", "\uD83C\uDDF9\uD83C\uDDF7"));
        load(new Currency("TTD", "TT$", "Trinidad and Tobago Dollar", "\uD83C\uDDF9\uD83C\uDDF9"));
        load(new Currency("TWD", "NT$", "New Taiwan Dollar", "\uD83C\uDDF9\uD83C\uDDFC"));
        load(new Currency("TZS", "x/y", "Tanzanian Shilling", "\uD83C\uDDF9\uD83C\uDDFF"));
        load(new Currency("UAH", "₴", "Ukrainian Hryvnia", "\uD83C\uDDF9\uD83C\uDDFF"));
        load(new Currency("UGX", "USh", "Ugandan Shilling", "\uD83C\uDDFA\uD83C\uDDEC"));
        load(new Currency("USD", "$", "US Dollar", "\uD83C\uDDFA\uD83C\uDDF8"));
        load(new Currency("UYU", "$U", "Uruguayan Peso", "\uD83C\uDDFA\uD83C\uDDFE"));
        load(new Currency("UZS", "лв", "Uzbekistan Som", "\uD83C\uDDFA\uD83C\uDDFF"));
        load(new Currency("VEF", "Bs.F", "Venezuelan Bolívar Fuerte", "\uD83C\uDDFB\uD83C\uDDEA"));
        load(new Currency("VND", "₫", "Vietnamese Dong", "\uD83C\uDDFB\uD83C\uDDF3"));
        load(new Currency("VUV", "Vt", "Vanuatu Vatu", "\uD83C\uDDFB\uD83C\uDDFA"));
        load(new Currency("WST", "WS$", "Samoan Tala", "\uD83C\uDDFC\uD83C\uDDF8"));
        load(new Currency("XAF", "FCFA", "CFA Franc BEAC", null));
        load(new Currency("XAG", "oz.", "Silver (troy ounce)", "\uD83E\uDD48"));
        load(new Currency("XAU", "oz.", "Gold (troy ounce)", "\uD83E\uDD47"));
        load(new Currency("XCD", "$", "East Caribbean Dollar", "\uD83C\uDDE6\uD83C\uDDEE"));
        load(new Currency("XOF", "CFA", "CFA Franc BCEAO", null));
        load(new Currency("XPF", "F", "CFP Franc", "\uD83C\uDDF5\uD83C\uDDEB"));
        load(new Currency("YER", "﷼", "Yemeni Rial", "\uD83C\uDDFE\uD83C\uDDEA"));
        load(new Currency("ZAR", "R", "South African Rand", "\uD83C\uDDFE\uD83C\uDDEA"));
        load(new Currency("ZMW", "ZK", "Zambian Kwacha", "\uD83C\uDDFF\uD83C\uDDF2"));
        load(new Currency("ZWL", "Z$", "Zimbabwean Dollar", "\uD83C\uDDFF\uD83C\uDDFC"));

        NOT_SUPPORTED_BY_HOUSTON.put(
                "ERN",
                new Currency("ERN", "Nfk", "Eritrean Nakfa", "\uD83C\uDDEA\uD83C\uDDF7")
        );
        NOT_SUPPORTED_BY_HOUSTON.put(
                "SSP",
                new Currency("SSP", ".ج.س", "Sudanese Pound", "\uD83C\uDDF8\uD83C\uDDE9")
        );
        NOT_SUPPORTED_BY_HOUSTON.put(
                "STD",
                new Currency("STD", "Db", "São Tomé and Príncipe Dobra", "\uD83C\uDDF8\uD83C\uDDF9")
        );
        NOT_SUPPORTED_BY_HOUSTON.put(
                "MRO",
                new Currency("MRO", "UM", "Mauritanian Ouguiya", "\uD83C\uDDF2\uD83C\uDDF7")
        );
        NOT_SUPPORTED_BY_HOUSTON.put(
                "SDP",
                new Currency("SDP", ".ج.س", "Sudanese Pound", "\uD83C\uDDF8\uD83C\uDDE9")
        );

        // Load not supported currencies so clients have the data for when houston supports them
        for (Currency value : NOT_SUPPORTED_BY_HOUSTON.values()) {
            load(value);
        }
    }

    private static void load(Currency currency) {
        CURRENCIES.put(currency.code, currency);
    }

    public static final Currency BTC = CURRENCIES.get("BTC");   // Our beloved currency :)
    public static final Currency USD = CURRENCIES.get("USD");   // Our default currency :|
    public static final Currency DEFAULT = USD;

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
        return Optional.ofNullable(CURRENCIES.get(currencyCode));
    }

    private final String code;

    private final String symbol;

    private final String name;

    private final String flag;  // Unicode char for country flag

    /**
     * Constructor.
     */
    public Currency(String code, String symbol, String name, String flag) {
        this.code = code;
        this.symbol = symbol;
        this.name = name;
        this.flag = flag;
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

    public String getFlag() {
        return flag;
    }
}
