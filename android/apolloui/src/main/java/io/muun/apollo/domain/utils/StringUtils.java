package io.muun.apollo.domain.utils;

import java.util.LinkedList;
import java.util.List;

public class StringUtils {

    /**
     * Return true if a String is null, empty or pure whitespace.
     */
    public static boolean isEmpty(String text) {
        return (text == null || text.trim().isEmpty());
    }

    /**
     * Join strings using a delimiter.
     */
    public static String join(String delimiter, String[] strings) {
        if (strings.length == 0) {
            return "";
        }

        final StringBuilder sb = new StringBuilder(strings[0]);

        for (int i = 1; i < strings.length; i++) {
            sb.append(delimiter);
            sb.append(strings[i]);
        }

        return sb.toString();
    }

    /**
     * Join strings using a delimiter, filtering empty items.
     */
    public static String joinText(String delimiter, String[] strings) {
        final List<String> nonEmptyStrings = new LinkedList<>();

        for (String string : strings) {
            if (! isEmpty(string)) {
                nonEmptyStrings.add(string);
            }
        }

        return join(delimiter, nonEmptyStrings.toArray(new String[nonEmptyStrings.size()]));
    }
}
