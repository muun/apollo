package io.muun.apollo.domain.model;

import io.muun.common.Optional;

import rx.functions.Func1;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class CountryInfo {

    private static List<CountryInfo> cachedCountryList;

    /**
     * Get the entire list of CountryInfo's sorted by name.
     */
    public static List<CountryInfo> getAll() {
        createCache();

        final ArrayList<CountryInfo> countries = new ArrayList<>(cachedCountryList);
        Collections.sort(countries, (o1, o2) -> o1.countryName.compareToIgnoreCase(o2.countryName));

        return countries;
    }

    /**
     * Find a CountryInfo item by country code (eg "AR")
     *
     * @implNote this is O(n), do NOT use it repeatedly.
     */
    public static Optional<CountryInfo> findByCode(String code) {
        return findBy(countryInfo -> countryInfo.countryCode.equals(code));
    }

    /**
     * Find a CountryInfo item by country number prefix (eg 54)
     *
     * @implNote this is O(n), do NOT use it repeatedly.
     */
    public static Optional<CountryInfo> findByNumber(int number) {
        return findBy(countryInfo -> countryInfo.countryNumber == number);
    }

    public final String countryName;
    public final String countryCode;
    public final Integer countryNumber;

    /**
     * Constructor.
     */
    public CountryInfo(String countryName, String countryCode, Integer countryNumber) {
        this.countryName = countryName;
        this.countryCode = countryCode;
        this.countryNumber = countryNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final CountryInfo that = (CountryInfo) o;
        return Objects.equals(countryCode, that.countryCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(countryCode);
    }

    @Override
    public String toString() {
        return countryName;
    }

    private static Optional<CountryInfo> findBy(Func1<CountryInfo, Boolean> f) {
        createCache();

        for (CountryInfo countryInfo : cachedCountryList) {
            if (f.call(countryInfo)) {
                return Optional.of(countryInfo);
            }
        }

        return Optional.empty();
    }

    private static void createCache() {
        if (cachedCountryList != null) {
            return;
        }

        cachedCountryList = Arrays.asList(
                // Countries with repeated codes (prioritized):
                new CountryInfo("United States", "US", 1),
                new CountryInfo("Russia", "RU", 7),
                new CountryInfo("United Kingdom", "GB", 44),
                new CountryInfo("Norway", "NO", 47),
                new CountryInfo("Australia", "AU", 61),
                new CountryInfo("Réunion", "RE", 262),
                new CountryInfo("Finland", "FI", 358),
                new CountryInfo("Guadeloupe", "GP", 590),
                new CountryInfo("Curaçao", "CW", 599),

                // Countries with unique codes:
                new CountryInfo("Afghanistan", "AF", 93),
                new CountryInfo("Albania", "AL", 355),
                new CountryInfo("Algeria", "DZ", 213),
                new CountryInfo("American Samoa", "AS", 1),
                new CountryInfo("Andorra", "AD", 376),
                new CountryInfo("Angola", "AO", 244),
                new CountryInfo("Anguilla", "AI", 1),
                new CountryInfo("Antigua & Barbuda", "AG", 1),
                new CountryInfo("Argentina", "AR", 54),
                new CountryInfo("Armenia", "AM", 374),
                new CountryInfo("Aruba", "AW", 297),
                new CountryInfo("Ascension Island", "AC", 247),
                new CountryInfo("Austria", "AT", 43),
                new CountryInfo("Azerbaijan", "AZ", 994),
                new CountryInfo("Bahamas", "BS", 1),
                new CountryInfo("Bahrain", "BH", 973),
                new CountryInfo("Bangladesh", "BD", 880),
                new CountryInfo("Barbados", "BB", 1),
                new CountryInfo("Belarus", "BY", 375),
                new CountryInfo("Belgium", "BE", 32),
                new CountryInfo("Belize", "BZ", 501),
                new CountryInfo("Benin", "BJ", 229),
                new CountryInfo("Bermuda", "BM", 1),
                new CountryInfo("Bhutan", "BT", 975),
                new CountryInfo("Bolivia", "BO", 591),
                new CountryInfo("Bosnia & Herzegovina", "BA", 387),
                new CountryInfo("Botswana", "BW", 267),
                new CountryInfo("Brazil", "BR", 55),
                new CountryInfo("British Indian Ocean Territory", "IO", 246),
                new CountryInfo("British Virgin Islands", "VG", 1),
                new CountryInfo("Brunei", "BN", 673),
                new CountryInfo("Bulgaria", "BG", 359),
                new CountryInfo("Burkina Faso", "BF", 226),
                new CountryInfo("Burundi", "BI", 257),
                new CountryInfo("Cambodia", "KH", 855),
                new CountryInfo("Cameroon", "CM", 237),
                new CountryInfo("Canada", "CA", 1),
                new CountryInfo("Cape Verde", "CV", 238),
                new CountryInfo("Caribbean Netherlands", "BQ", 599),
                new CountryInfo("Cayman Islands", "KY", 1),
                new CountryInfo("Central African Republic", "CF", 236),
                new CountryInfo("Chad", "TD", 235),
                new CountryInfo("Chile", "CL", 56),
                new CountryInfo("China", "CN", 86),
                new CountryInfo("Christmas Island", "CX", 61),
                new CountryInfo("Cocos (Keeling) Islands", "CC", 61),
                new CountryInfo("Colombia", "CO", 57),
                new CountryInfo("Comoros", "KM", 269),
                new CountryInfo("Congo (DRC)", "CD", 243),
                new CountryInfo("Congo (Republic)", "CG", 242),
                new CountryInfo("Cook Islands", "CK", 682),
                new CountryInfo("Costa Rica", "CR", 506),
                new CountryInfo("Croatia", "HR", 385),
                new CountryInfo("Cuba", "CU", 53),
                new CountryInfo("Cyprus", "CY", 357),
                new CountryInfo("Czech Republic", "CZ", 420),
                new CountryInfo("Côte d’Ivoire", "CI", 225),
                new CountryInfo("Denmark", "DK", 45),
                new CountryInfo("Djibouti", "DJ", 253),
                new CountryInfo("Dominica", "DM", 1),
                new CountryInfo("Dominican Republic", "DO", 1),
                new CountryInfo("Ecuador", "EC", 593),
                new CountryInfo("Egypt", "EG", 20),
                new CountryInfo("El Salvador", "SV", 503),
                new CountryInfo("Equatorial Guinea", "GQ", 240),
                new CountryInfo("Eritrea", "ER", 291),
                new CountryInfo("Estonia", "EE", 372),
                new CountryInfo("Ethiopia", "ET", 251),
                new CountryInfo("Falkland Islands", "FK", 500),
                new CountryInfo("Faroe Islands", "FO", 298),
                new CountryInfo("Fiji", "FJ", 679),
                new CountryInfo("France", "FR", 33),
                new CountryInfo("French Guiana", "GF", 594),
                new CountryInfo("French Polynesia", "PF", 689),
                new CountryInfo("Gabon", "GA", 241),
                new CountryInfo("Gambia", "GM", 220),
                new CountryInfo("Georgia", "GE", 995),
                new CountryInfo("Germany", "DE", 49),
                new CountryInfo("Ghana", "GH", 233),
                new CountryInfo("Gibraltar", "GI", 350),
                new CountryInfo("Greece", "GR", 30),
                new CountryInfo("Greenland", "GL", 299),
                new CountryInfo("Grenada", "GD", 1),
                new CountryInfo("Guam", "GU", 1),
                new CountryInfo("Guatemala", "GT", 502),
                new CountryInfo("Guernsey", "GG", 44),
                new CountryInfo("Guinea", "GN", 224),
                new CountryInfo("Guinea-Bissau", "GW", 245),
                new CountryInfo("Guyana", "GY", 592),
                new CountryInfo("Haiti", "HT", 509),
                new CountryInfo("Honduras", "HN", 504),
                new CountryInfo("Hong Kong", "HK", 852),
                new CountryInfo("Hungary", "HU", 36),
                new CountryInfo("Iceland", "IS", 354),
                new CountryInfo("India", "IN", 91),
                new CountryInfo("Indonesia", "ID", 62),
                new CountryInfo("Iran", "IR", 98),
                new CountryInfo("Iraq", "IQ", 964),
                new CountryInfo("Ireland", "IE", 353),
                new CountryInfo("Isle of Man", "IM", 44),
                new CountryInfo("Israel", "IL", 972),
                new CountryInfo("Italy", "IT", 39),
                new CountryInfo("Jamaica", "JM", 1),
                new CountryInfo("Japan", "JP", 81),
                new CountryInfo("Jersey", "JE", 44),
                new CountryInfo("Jordan", "JO", 962),
                new CountryInfo("Kazakhstan", "KZ", 7),
                new CountryInfo("Kenya", "KE", 254),
                new CountryInfo("Kiribati", "KI", 686),
                new CountryInfo("Kuwait", "KW", 965),
                new CountryInfo("Kyrgyzstan", "KG", 996),
                new CountryInfo("Laos", "LA", 856),
                new CountryInfo("Latvia", "LV", 371),
                new CountryInfo("Lebanon", "LB", 961),
                new CountryInfo("Lesotho", "LS", 266),
                new CountryInfo("Liberia", "LR", 231),
                new CountryInfo("Libya", "LY", 218),
                new CountryInfo("Liechtenstein", "LI", 423),
                new CountryInfo("Lithuania", "LT", 370),
                new CountryInfo("Luxembourg", "LU", 352),
                new CountryInfo("Macau", "MO", 853),
                new CountryInfo("Macedonia (FYROM)", "MK", 389),
                new CountryInfo("Madagascar", "MG", 261),
                new CountryInfo("Malawi", "MW", 265),
                new CountryInfo("Malaysia", "MY", 60),
                new CountryInfo("Maldives", "MV", 960),
                new CountryInfo("Mali", "ML", 223),
                new CountryInfo("Malta", "MT", 356),
                new CountryInfo("Marshall Islands", "MH", 692),
                new CountryInfo("Martinique", "MQ", 596),
                new CountryInfo("Mauritania", "MR", 222),
                new CountryInfo("Mauritius", "MU", 230),
                new CountryInfo("Mayotte", "YT", 262),
                new CountryInfo("Mexico", "MX", 52),
                new CountryInfo("Micronesia", "FM", 691),
                new CountryInfo("Moldova", "MD", 373),
                new CountryInfo("Monaco", "MC", 377),
                new CountryInfo("Mongolia", "MN", 976),
                new CountryInfo("Montenegro", "ME", 382),
                new CountryInfo("Montserrat", "MS", 1),
                new CountryInfo("Morocco", "MA", 212),
                new CountryInfo("Mozambique", "MZ", 258),
                new CountryInfo("Myanmar (Burma)", "MM", 95),
                new CountryInfo("Namibia", "NA", 264),
                new CountryInfo("Nauru", "NR", 674),
                new CountryInfo("Nepal", "NP", 977),
                new CountryInfo("Netherlands", "NL", 31),
                new CountryInfo("New Caledonia", "NC", 687),
                new CountryInfo("New Zealand", "NZ", 64),
                new CountryInfo("Nicaragua", "NI", 505),
                new CountryInfo("Niger", "NE", 227),
                new CountryInfo("Nigeria", "NG", 234),
                new CountryInfo("Niue", "NU", 683),
                new CountryInfo("Norfolk Island", "NF", 672),
                new CountryInfo("North Korea", "KP", 850),
                new CountryInfo("Northern Mariana Islands", "MP", 1),
                new CountryInfo("Oman", "OM", 968),
                new CountryInfo("Pakistan", "PK", 92),
                new CountryInfo("Palau", "PW", 680),
                new CountryInfo("Palestine", "PS", 970),
                new CountryInfo("Panama", "PA", 507),
                new CountryInfo("Papua New Guinea", "PG", 675),
                new CountryInfo("Paraguay", "PY", 595),
                new CountryInfo("Peru", "PE", 51),
                new CountryInfo("Philippines", "PH", 63),
                new CountryInfo("Poland", "PL", 48),
                new CountryInfo("Portugal", "PT", 351),
                new CountryInfo("Puerto Rico", "PR", 1),
                new CountryInfo("Qatar", "QA", 974),
                new CountryInfo("Romania", "RO", 40),
                new CountryInfo("Rwanda", "RW", 250),
                new CountryInfo("Samoa", "WS", 685),
                new CountryInfo("San Marino", "SM", 378),
                new CountryInfo("Saudi Arabia", "SA", 966),
                new CountryInfo("Senegal", "SN", 221),
                new CountryInfo("Serbia", "RS", 381),
                new CountryInfo("Seychelles", "SC", 248),
                new CountryInfo("Sierra Leone", "SL", 232),
                new CountryInfo("Singapore", "SG", 65),
                new CountryInfo("Sint Maarten", "SX", 1),
                new CountryInfo("Slovakia", "SK", 421),
                new CountryInfo("Slovenia", "SI", 386),
                new CountryInfo("Solomon Islands", "SB", 677),
                new CountryInfo("Somalia", "SO", 252),
                new CountryInfo("South Africa", "ZA", 27),
                new CountryInfo("South Korea", "KR", 82),
                new CountryInfo("South Sudan", "SS", 211),
                new CountryInfo("Spain", "ES", 34),
                new CountryInfo("Sri Lanka", "LK", 94),
                new CountryInfo("St. Barthélemy", "BL", 590),
                new CountryInfo("St. Helena", "SH", 290),
                new CountryInfo("St. Kitts & Nevis", "KN", 1),
                new CountryInfo("St. Lucia", "LC", 1),
                new CountryInfo("St. Martin", "MF", 590),
                new CountryInfo("St. Pierre & Miquelon", "PM", 508),
                new CountryInfo("St. Vincent & Grenadines", "VC", 1),
                new CountryInfo("Sudan", "SD", 249),
                new CountryInfo("Suriname", "SR", 597),
                new CountryInfo("Svalbard & Jan Mayen", "SJ", 47),
                new CountryInfo("Swaziland", "SZ", 268),
                new CountryInfo("Sweden", "SE", 46),
                new CountryInfo("Switzerland", "CH", 41),
                new CountryInfo("Syria", "SY", 963),
                new CountryInfo("São Tomé & Príncipe", "ST", 239),
                new CountryInfo("Taiwan", "TW", 886),
                new CountryInfo("Tajikistan", "TJ", 992),
                new CountryInfo("Tanzania", "TZ", 255),
                new CountryInfo("Thailand", "TH", 66),
                new CountryInfo("Timor-Leste", "TL", 670),
                new CountryInfo("Togo", "TG", 228),
                new CountryInfo("Tokelau", "TK", 690),
                new CountryInfo("Tonga", "TO", 676),
                new CountryInfo("Trinidad & Tobago", "TT", 1),
                new CountryInfo("Tunisia", "TN", 216),
                new CountryInfo("Turkey", "TR", 90),
                new CountryInfo("Turkmenistan", "TM", 993),
                new CountryInfo("Turks & Caicos Islands", "TC", 1),
                new CountryInfo("Tuvalu", "TV", 688),
                new CountryInfo("U.S. Virgin Islands", "VI", 1),
                new CountryInfo("Uganda", "UG", 256),
                new CountryInfo("Ukraine", "UA", 380),
                new CountryInfo("United Arab Emirates", "AE", 971),
                new CountryInfo("Uruguay", "UY", 598),
                new CountryInfo("Uzbekistan", "UZ", 998),
                new CountryInfo("Vanuatu", "VU", 678),
                new CountryInfo("Vatican City", "VA", 379),
                new CountryInfo("Venezuela", "VE", 58),
                new CountryInfo("Vietnam", "VN", 84),
                new CountryInfo("Wallis & Futuna", "WF", 681),
                new CountryInfo("Western Sahara", "EH", 212),
                new CountryInfo("Yemen", "YE", 967),
                new CountryInfo("Zambia", "ZM", 260),
                new CountryInfo("Zimbabwe", "ZW", 263),
                new CountryInfo("Åland Islands", "AX", 358)
        );
    }
}
