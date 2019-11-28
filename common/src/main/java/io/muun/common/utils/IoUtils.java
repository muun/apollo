package io.muun.common.utils;

import java.io.InputStream;

public class IoUtils {

    private IoUtils() {}

    public static String toString(InputStream is) {
        final java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }
}
