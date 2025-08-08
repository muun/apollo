package io.muun.apollo.domain.utils;

import org.threeten.bp.ZoneId;
import org.threeten.bp.ZoneOffset;
import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.format.DateTimeFormatter;
import org.threeten.bp.format.DateTimeParseException;
import org.threeten.bp.zone.ZoneRulesException;

public class DateUtils {

    /**
     * Return the current ZonedDateTime, in UTC.
     */
    public static ZonedDateTime now() {
        return ZonedDateTime.now(ZoneOffset.UTC);
    }

    /**
     * Convert a ZonedDateTime to the system's default timezone, or to UTC if not valid.
     *
     * <p>NOTE: the invalid zone scenario can happen. We've ran the application in devices that
     * report non-existent time zones. Can also happen if timezones change (in real life), but our
     * library doesn't keep up.</p>
     */
    public static ZonedDateTime toSystemDefault(ZonedDateTime dateTime) {
        try {
            return toTimeZone(dateTime, ZoneId.systemDefault());

        } catch (ZoneRulesException ex) {
            return toUtc(dateTime);
        }
    }

    /**
     * Convert a ZonedDateTime to UTC.
     */
    public static ZonedDateTime toUtc(ZonedDateTime dateTime) {
        return toTimeZone(dateTime, ZoneOffset.UTC);
    }

    /**
     * Convert a ZonedDateTime to a given timezone.
     */
    public static ZonedDateTime toTimeZone(ZonedDateTime dateTime, ZoneId zoneId) {
        return dateTime.withZoneSameInstant(zoneId);
    }

    /**
     * Render a ZonedDateTime as an ISO string.
     */
    public static String toIsoString(ZonedDateTime dateTime) {
        return dateTime.format(DateTimeFormatter.ISO_DATE_TIME);
    }

    public static ZonedDateTime fromIsoString(String isoString) throws DateTimeParseException {
        return ZonedDateTime.parse(isoString, DateTimeFormatter.ISO_DATE_TIME);
    }

    /**
     * Return whether two ZonedDateTimes represent the same instant in time.
     */
    public static boolean isEqual(ZonedDateTime a, ZonedDateTime b) {
        // NOTE: we should not compare ZonedDateTimes using `equals()`, since that will fail when
        // two instances have different zones with the same offset. These point to the same moment
        // in time, but the zone name is different (eg Z vs UTC), and `equals()` fails.
        return a.toInstant().equals(b.toInstant());
    }
}
