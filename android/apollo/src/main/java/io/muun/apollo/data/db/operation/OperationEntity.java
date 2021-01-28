package io.muun.apollo.data.db.operation;

import io.muun.apollo.data.db.base.BaseEntity;
import io.muun.apollo.data.db.incoming_swap.IncomingSwapEntity;
import io.muun.apollo.data.db.incoming_swap.IncomingSwapHtlcEntity;
import io.muun.apollo.data.db.public_profile.PublicProfileEntity;
import io.muun.apollo.data.db.submarine_swap.SubmarineSwapEntity;
import io.muun.apollo.domain.model.BitcoinAmount;
import io.muun.apollo.domain.model.IncomingSwap;
import io.muun.apollo.domain.model.Operation;
import io.muun.apollo.domain.model.PublicProfile;
import io.muun.apollo.domain.model.SubmarineSwap;
import io.muun.common.model.OperationDirection;
import io.muun.common.model.OperationStatus;

import android.database.Cursor;
import androidx.annotation.Nullable;
import androidx.sqlite.db.SupportSQLiteDatabase;
import com.google.auto.value.AutoValue;
import com.squareup.sqldelight.prerelease.ColumnAdapter;
import com.squareup.sqldelight.prerelease.EnumColumnAdapter;
import com.squareup.sqldelight.prerelease.SqlDelightStatement;

@AutoValue
public abstract class OperationEntity implements OperationModel, BaseEntity {

    private static final ColumnAdapter<OperationDirection, String> OPERATION_DIRECTION_ADAPTER =
            EnumColumnAdapter.create(OperationDirection.class);

    private static final ColumnAdapter<OperationStatus, String> OPERATION_STATUS_ADAPTER =
            EnumColumnAdapter.create(OperationStatus.class);

    public static final Factory<OperationEntity> FACTORY = new Factory<>(
            AutoValue_OperationEntity::new,
            OPERATION_DIRECTION_ADAPTER,
            MONETARY_AMOUNT_ADAPTER,
            MONETARY_AMOUNT_ADAPTER,
            MONETARY_AMOUNT_ADAPTER,
            MONETARY_AMOUNT_ADAPTER,
            OPERATION_STATUS_ADAPTER,
            ZONED_DATE_TIME_ADAPTER
    );

    @AutoValue
    public abstract static class CompleteOperation implements OperationModel.SelectAllModel<
            OperationEntity,
            PublicProfileEntity,
            SubmarineSwapEntity,
            IncomingSwapEntity,
            IncomingSwapHtlcEntity> {
    }

    /**
     * Map from the model to the content values.
     */
    public static SqlDelightStatement fromModel(SupportSQLiteDatabase db, Operation operation) {

        final PublicProfile senderProfile = operation.senderProfile;
        final PublicProfile receiverProfile = operation.receiverProfile;

        final OperationModel.InsertOperation insertStatement = new OperationModel
                .InsertOperation(db, FACTORY);


        insertStatement.bind(
                operation.getId() == null ? BaseEntity.NULL_ID : operation.getId(),
                operation.getHid(),
                operation.direction,
                operation.isExternal,
                senderProfile == null ? null : senderProfile.getHid(),
                operation.senderIsExternal,
                receiverProfile == null ? null : receiverProfile.getHid(),
                operation.receiverIsExternal,
                operation.receiverAddress,
                operation.receiverAddressDerivationPath,
                operation.amount.inSatoshis,
                operation.amount.inInputCurrency,
                operation.amount.inPrimaryCurrency,
                operation.fee.inSatoshis,
                operation.fee.inInputCurrency,
                operation.fee.inPrimaryCurrency,
                operation.confirmations,
                operation.hash,
                operation.description,
                operation.status,
                operation.creationDate,
                operation.exchangeRateWindowHid,
                operation.swap == null ? null : operation.swap.houstonUuid,
                operation.incomingSwap == null ? null : operation.incomingSwap.houstonUuid,
                operation.isRbf
        );

        return insertStatement;
    }

    /**
     * Map from the database cursor to the model.
     */
    public static Operation toModel(Cursor cursor) {

        final CompleteOperation entity = FACTORY.selectAllMapper(
                AutoValue_OperationEntity_CompleteOperation::new,
                PublicProfileEntity.FACTORY,
                SubmarineSwapEntity.FACTORY,
                IncomingSwapEntity.FACTORY,
                IncomingSwapHtlcEntity.FACTORY
        ).map(cursor);

        return new Operation(
                entity.operations().id(),
                entity.operations().hid(),
                entity.operations().direction(),
                entity.operations().is_external(),
                getPublicProfile(entity.sender_profile()),
                entity.operations().sender_is_external(),
                getPublicProfile(entity.receiver_profile()),
                entity.operations().receiver_is_external(),
                entity.operations().receiver_address(),
                entity.operations().receiver_address_derivation_path(),
                new BitcoinAmount(
                        entity.operations().amount_in_satoshis(),
                        entity.operations().amount_in_input_currency(),
                        entity.operations().amount_in_primary_currency()
                ),
                new BitcoinAmount(
                        entity.operations().fee_in_satoshis(),
                        entity.operations().fee_in_input_currency(),
                        entity.operations().fee_in_primary_currency()
                ),
                entity.operations().confirmations(),
                entity.operations().hash(),
                entity.operations().description(),
                entity.operations().status(),
                entity.operations().creation_date(),
                entity.operations().exchange_rate_window_hid(),
                getSwap(entity.swap()),
                getIncomingSwap(entity.incoming_swap(), entity.htlc()),
                entity.operations().is_rbf()
        );
    }

    @Nullable
    private static PublicProfile getPublicProfile(PublicProfileEntity profile) {
        return profile == null ? null : PublicProfileEntity.getPublicProfile(profile);
    }

    @Nullable
    private static SubmarineSwap getSwap(SubmarineSwapEntity swap) {
        return swap == null ? null : SubmarineSwapEntity.getSubmarineSwap(swap);
    }

    @Nullable
    private static IncomingSwap getIncomingSwap(final IncomingSwapEntity swap,
                                                final IncomingSwapHtlcEntity htlc) {
        return swap == null ? null : IncomingSwapEntity.getIncomingSwap(swap, htlc);
    }
}
