package io.muun.apollo.data.db.operation;

import io.muun.apollo.data.db.base.BaseEntity;
import io.muun.apollo.data.db.public_profile.PublicProfileEntity;
import io.muun.apollo.data.db.public_profile.PublicProfileModel;
import io.muun.apollo.domain.model.BitcoinAmount;
import io.muun.apollo.domain.model.Operation;
import io.muun.apollo.domain.model.PublicProfile;
import io.muun.common.model.OperationDirection;
import io.muun.common.model.OperationStatus;

import android.content.ContentValues;
import android.database.Cursor;
import android.support.annotation.Nullable;
import com.google.auto.value.AutoValue;
import com.squareup.sqldelight.ColumnAdapter;
import com.squareup.sqldelight.EnumColumnAdapter;

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
    public abstract static class CompleteOperation implements
            OperationModel.SelectAllModel<OperationEntity, PublicProfileEntity> {
    }

    /**
     * Map from the model to the content values.
     */
    public static ContentValues fromModel(Operation operation) {

        final PublicProfile senderProfile = operation.senderProfile;
        final PublicProfile receiverProfile = operation.receiverProfile;

        return FACTORY.marshal()
                .id(operation.id == null ? BaseEntity.NULL_ID : operation.id)
                .hid(operation.hid)
                .direction(operation.direction)
                .is_external(operation.isExternal)
                .sender_hid(senderProfile == null ? null : senderProfile.hid)
                .sender_is_external(operation.senderIsExternal)
                .receiver_hid(receiverProfile == null ? null : receiverProfile.hid)
                .receiver_is_external(operation.receiverIsExternal)
                .receiver_address(operation.receiverAddress)
                .receiver_address_derivation_path(operation.receiverAddressDerivationPath)
                .amount_in_satoshis(operation.amount.inSatoshis)
                .amount_in_input_currency(operation.amount.inInputCurrency)
                .amount_in_primary_currency(operation.amount.inPrimaryCurrency)
                .fee_in_satoshis(operation.fee.inSatoshis)
                .fee_in_input_currency(operation.fee.inInputCurrency)
                .fee_in_primary_currency(operation.fee.inPrimaryCurrency)
                .confirmations(operation.confirmations)
                .hash(operation.hash)
                .description(operation.description)
                .status(operation.status)
                .creation_date(operation.creationDate)
                .exchange_rate_window_hid(operation.exchangeRateWindowHid)
                .asContentValues();
    }

    /**
     * Map from the database cursor to the model.
     */
    public static Operation toModel(Cursor cursor) {

        final CompleteOperation entity = FACTORY.selectAllMapper(
                AutoValue_OperationEntity_CompleteOperation::new,
                PublicProfileEntity.FACTORY
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
                entity.operations().exchange_rate_window_hid()
        );
    }

    @Nullable
    private static PublicProfile getPublicProfile(PublicProfileModel profile) {

        return profile == null ? null : new PublicProfile(
                profile.id(),
                profile.hid(),
                profile.first_name(),
                profile.last_name(),
                profile.profile_picture_url()
        );
    }
}
