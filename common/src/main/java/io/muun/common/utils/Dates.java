package io.muun.common.utils;

import org.threeten.bp.format.DateTimeFormatter;

public final class Dates {

    // Ideally, we would like to use DateTimeFormatter.ISO_DATE_TIME but it doesn't handle millis.
    // This does the exact same thing, plus handling of milliseconds.
    // More info on: https://bit.ly/2RP3EAt
    public static final DateTimeFormatter LN_DATE_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX");

}
