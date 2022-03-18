package io.muun.apollo.data.db.base;

import io.muun.apollo.data.db.operation.Operations;
import io.muun.apollo.data.db.operation.Submarine_swaps;
import io.muun.apollo.data.serialization.SerializationUtils;
import io.muun.common.api.OperationMetadataJson;
import io.muun.common.model.DebtType;
import io.muun.common.model.OperationDirection;
import io.muun.common.model.OperationStatus;

import com.squareup.sqldelight.ColumnAdapter;
import com.squareup.sqldelight.EnumColumnAdapter;
import org.threeten.bp.ZonedDateTime;

import javax.annotation.Nonnull;
import javax.money.MonetaryAmount;
import javax.validation.constraints.NotNull;

public interface Adapters {

    ColumnAdapter<ZonedDateTime, String> ZONED_DATE_TIME =
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

    ColumnAdapter<MonetaryAmount, String> MONETARY_AMOUNT =
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

    /**
     * Build an adapter for a given type from and to JSON.
     */
    static <T> ColumnAdapter<T, String> json(final Class<T> type) {
        return new ColumnAdapter<T, String>() {

            @Nonnull
            @Override
            public T decode(String databaseValue) {
                return SerializationUtils.deserializeJson(type, databaseValue);
            }

            @Override
            public String encode(@Nonnull T value) {
                return SerializationUtils.serializeJson(type, value);
            }

        };
    }

    Operations.Adapter OPERATIONS = new Operations.Adapter(
            new EnumColumnAdapter<>(OperationDirection.values()),
            Adapters.MONETARY_AMOUNT,
            Adapters.MONETARY_AMOUNT,
            Adapters.MONETARY_AMOUNT,
            Adapters.MONETARY_AMOUNT,
            new EnumColumnAdapter<>(OperationStatus.values()),
            Adapters.ZONED_DATE_TIME,
            Adapters.json(OperationMetadataJson.class)
    );

    Submarine_swaps.Adapter SUBMARINE_SWAPS = new Submarine_swaps.Adapter(
            Adapters.ZONED_DATE_TIME,
            Adapters.ZONED_DATE_TIME,
            new EnumColumnAdapter<>(DebtType.values())
    );

}
