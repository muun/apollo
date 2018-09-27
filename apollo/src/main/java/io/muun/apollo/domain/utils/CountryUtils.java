package io.muun.apollo.domain.utils;

import java.util.HashMap;
import java.util.Map;

public class CountryUtils {

    private static final Map<Integer, String> phonePrefixToMostPopularCountryCode =
            new HashMap<Integer, String>() {
        {
            put(1, "US");
            put(7, "RU");
            put(44, "GB");
            put(47, "NO");
            put(61, "AU");
            put(262, "RE");
            put(358, "FI");
            put(590, "GP");
            put(599, "CW");
            }
    };

    public static boolean hasMultipleCountries(int phonePrefix) {
        return phonePrefixToMostPopularCountryCode.containsKey(phonePrefix);
    }

    public static String mostPopularCountryCodeForPrefix(int phonePrefix) {
        return phonePrefixToMostPopularCountryCode.get(phonePrefix);
    }
}
