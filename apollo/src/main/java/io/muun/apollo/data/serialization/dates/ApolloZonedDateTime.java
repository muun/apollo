package io.muun.apollo.data.serialization.dates;

import io.muun.apollo.domain.utils.DateUtils;
import io.muun.common.dates.MuunZonedDateTime;
import io.muun.common.utils.Preconditions;

import org.threeten.bp.ZonedDateTime;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

public class ApolloZonedDateTime implements MuunZonedDateTime {

    public final ZonedDateTime dateTime;

    public static ApolloZonedDateTime fromString(@NotNull String dateString) {
        return new ApolloZonedDateTime(DateUtils.fromIsoString(dateString));
    }

    @Nullable
    public static ApolloZonedDateTime fromNullable(@Nullable ZonedDateTime dateTime) {
        return dateTime == null ? null : of(dateTime);
    }

    @NotNull
    public static ApolloZonedDateTime of(@NotNull ZonedDateTime dateTime) {
        return new ApolloZonedDateTime(dateTime);
    }

    private ApolloZonedDateTime(@NotNull ZonedDateTime dateTime) {
        Preconditions.checkNotNull(dateTime);
        this.dateTime = dateTime;
    }

    @Override
    public String toString() {
        return DateUtils.toIsoString(dateTime);
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
