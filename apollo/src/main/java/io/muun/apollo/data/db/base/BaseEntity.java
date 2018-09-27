package io.muun.apollo.data.db.base;

import io.muun.apollo.data.serialization.SerializationUtils;

import com.squareup.sqldelight.ColumnAdapter;
import org.threeten.bp.ZonedDateTime;

import javax.money.MonetaryAmount;
import javax.validation.constraints.NotNull;

public interface BaseEntity {

    ColumnAdapter<ZonedDateTime, String> ZONED_DATE_TIME_ADAPTER =
            new ColumnAdapter<ZonedDateTime, String>() {
                @NotNull
                @Override
                public ZonedDateTime decode(String databaseValue) {
                    return SerializationUtils.deserializeDate(databaseValue);
                }

                @Override
                public String encode(@NotNull ZonedDateTime value) {
                    return SerializationUtils.serializeDate(value);
                }
            };

    ColumnAdapter<MonetaryAmount, String> MONETARY_AMOUNT_ADAPTER =
            new ColumnAdapter<MonetaryAmount, String>() {
                @NotNull
                @Override
                public MonetaryAmount decode(String databaseValue) {
                    return SerializationUtils.deserializeMonetaryAmount(databaseValue);
                }

                @Override
                public String encode(@NotNull MonetaryAmount value) {
                    return SerializationUtils.serializeMonetaryAmount(value);
                }
            };

    long NULL_ID = 0L;

    long id();
}
