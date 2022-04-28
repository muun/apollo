package io.muun.common.utils;

import org.threeten.bp.format.DateTimeFormatter;

public final class Dates {

    public static final DateTimeFormatter ISO_DATE_TIME_WITH_MILLIS = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX");

    // Ideally, we would like to use DateTimeFormatter.ISO_DATE_TIME but it doesn't handle millis.
    // This does the exact same thing, plus handling of milliseconds.
    // More info on: https://bit.ly/2RP3EAt
    public static final DateTimeFormatter LN_DATE_TIME = ISO_DATE_TIME_WITH_MILLIS;

    public static final Long MINUTE_IN_SECONDS = 60L;
    public static final Long HOUR_IN_SECONDS = MINUTE_IN_SECONDS * 60;
    public static final Long DAY_IN_SECONDS = HOUR_IN_SECONDS * 24;
    public static final Long WEEK_IN_SECONDS = DAY_IN_SECONDS * 7;
}
