package io.muun.apollo.data.serialization.dates;

import io.muun.common.dates.MuunZonedDateTime;

import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.format.DateTimeFormatter;

import java.util.Objects;

public class ApolloZonedDateTime implements MuunZonedDateTime {

    public final ZonedDateTime dateTime;

    public static ApolloZonedDateTime fromString(String dateString) {
        final ZonedDateTime date = ZonedDateTime.parse(dateString, DateTimeFormatter.ISO_DATE_TIME);
        return new ApolloZonedDateTime(date);
    }

    public ApolloZonedDateTime(ZonedDateTime dateTime) {
        this.dateTime = dateTime;
    }

    @Override
    public String toString() {
        return dateTime.format(DateTimeFormatter.ISO_DATE_TIME);
    }

    @Override
    public boolean equals(Object other) {

        if (this == other) {
            return true;
        }

        if (!(other instanceof ApolloZonedDateTime)) {
            return false;
        }

        final ApolloZonedDateTime that = (ApolloZonedDateTime) other;
        return Objects.equals(dateTime, that.dateTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dateTime);
    }
}
