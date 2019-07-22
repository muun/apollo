package io.muun.apollo.data.db.submarine_swap;

import io.muun.apollo.data.db.base.BaseEntity;
import io.muun.apollo.data.db.operation.SubmarineSwapModel;
import io.muun.apollo.domain.model.SubmarineSwap;
import io.muun.apollo.domain.model.SubmarineSwapFundingOutput;
import io.muun.apollo.domain.model.SubmarineSwapReceiver;
import io.muun.common.crypto.hd.MuunAddress;

import android.content.ContentValues;
import android.database.Cursor;
import android.support.annotation.NonNull;
import com.google.auto.value.AutoValue;

@AutoValue
public abstract class SubmarineSwapEntity implements SubmarineSwapModel, BaseEntity {

    public static final SubmarineSwapModel.Factory<SubmarineSwapEntity>
            FACTORY = new SubmarineSwapModel.Factory<>(
            AutoValue_SubmarineSwapEntity::new,
            ZONED_DATE_TIME_ADAPTER,
            ZONED_DATE_TIME_ADAPTER
    );

    /**
     * Map from the model to the content values.
     */
    public static ContentValues fromModel(SubmarineSwap swap) {

        final SubmarineSwapReceiver receiver = swap.getReceiver();
        final SubmarineSwapFundingOutput fundingOutput = swap.getFundingOutput();
        final MuunAddress userRefundAddress = fundingOutput.getUserRefundAddress();

        return FACTORY.marshal()
                .id(swap.id == null ? BaseEntity.NULL_ID : swap.id)
                .houston_uuid(swap.houstonUuid)
                .invoice(swap.getInvoice())
                .receiver_alias(receiver.getAlias())
                .receiver_network_addresses(receiver.getSerializedNetworkAddresses())
                .receiver_public_key(receiver.getPublicKey())
                .funding_output_address(fundingOutput.getOutputAddress())
                .funding_output_amount_in_satoshis(fundingOutput.getOutputAmountInSatoshis())
                .funding_confirmations_needed(fundingOutput.getConfirmationsNeeded())
                .funding_user_lock_time(fundingOutput.getUserLockTime())
                .funding_user_refund_address(userRefundAddress.getAddress())
                .funding_user_refund_address_path(userRefundAddress.getDerivationPath())
                .funding_user_refund_address_version(userRefundAddress.getVersion())
                .funding_server_payment_hash_in_hex(fundingOutput.getServerPaymentHashInHex())
                .funding_server_public_key_in_hex(fundingOutput.getServerPublicKeyInHex())
                .sweep_fee_in_satoshis(swap.getSweepFeeInSatoshis())
                .lightning_fee_in_satoshis(swap.getLightningFeeInSatoshis())
                .expires_at(swap.getExpiresAt())
                .payed_at(swap.getPayedAt())
                .preimage_in_hex(swap.getPreimageInHex())
                .asContentValues();
    }

    /**
     * Map from the database cursor to the model.
     */
    public static SubmarineSwap toModel(Cursor cursor) {

        final SubmarineSwapEntity entity = FACTORY.selectAllMapper().map(cursor);

        return getSubmarineSwap(entity);
    }

    /**
     * Builds a SubmarineSwap domain layer model from a data layer SubmarineSwapEntity.
     */
    @NonNull
    public static SubmarineSwap getSubmarineSwap(SubmarineSwapEntity entity) {
        return new SubmarineSwap(
                entity.id(),
                entity.houston_uuid(),
                entity.invoice(),
                new SubmarineSwapReceiver(
                        entity.receiver_alias(),
                        entity.receiver_network_addresses(),
                        entity.receiver_public_key()
                ),
                new SubmarineSwapFundingOutput(
                        entity.funding_output_address(),
                        entity.funding_output_amount_in_satoshis(),
                        (int) entity.funding_confirmations_needed(),
                        (int) entity.funding_user_lock_time(),
                        new MuunAddress(
                                (int) entity.funding_user_refund_address_version(),
                                entity.funding_user_refund_address_path(),
                                entity.funding_user_refund_address()
                        ),
                        entity.funding_server_payment_hash_in_hex(),
                        entity.funding_server_public_key_in_hex()
                ),
                entity.sweep_fee_in_satoshis(),
                entity.lightning_fee_in_satoshis(),
                entity.expires_at(),
                entity.payed_at(),
                entity.preimage_in_hex()
        );
    }
}
