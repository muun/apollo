package io.muun.apollo.data.db.submarine_swap;

import io.muun.apollo.data.db.base.BaseEntity;
import io.muun.apollo.data.db.operation.SubmarineSwapModel;
import io.muun.apollo.domain.model.SubmarineSwap;
import io.muun.apollo.domain.model.SubmarineSwapFees;
import io.muun.apollo.domain.model.SubmarineSwapFundingOutput;
import io.muun.apollo.domain.model.SubmarineSwapReceiver;
import io.muun.common.crypto.hd.MuunAddress;
import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.model.DebtType;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.sqlite.db.SupportSQLiteDatabase;
import com.google.auto.value.AutoValue;
import com.squareup.sqldelight.prerelease.EnumColumnAdapter;
import com.squareup.sqldelight.prerelease.SqlDelightStatement;

@AutoValue
public abstract class SubmarineSwapEntity implements SubmarineSwapModel, BaseEntity {

    public static final SubmarineSwapModel.Factory<SubmarineSwapEntity>
            FACTORY = new SubmarineSwapModel.Factory<>(
            AutoValue_SubmarineSwapEntity::new,
            ZONED_DATE_TIME_ADAPTER,
            ZONED_DATE_TIME_ADAPTER,
            EnumColumnAdapter.create(DebtType.class)
    );

    /**
     * Map from the model to the content values.
     */
    public static SqlDelightStatement fromModel(SupportSQLiteDatabase db, SubmarineSwap swap) {

        final SubmarineSwapReceiver receiver = swap.getReceiver();
        final SubmarineSwapFundingOutput fundingOutput = swap.getFundingOutput();
        final MuunAddress userRefundAddress = fundingOutput.getUserRefundAddress();

        final SubmarineSwapModel.InsertSwap insertStatement = new SubmarineSwapModel
                .InsertSwap(db, FACTORY);

        final Integer userLockTime = fundingOutput.getUserLockTime();
        final Integer expirationInBlocks = fundingOutput.getExpirationInBlocks();
        final PublicKey userPublicKey = fundingOutput.getUserPublicKey();
        final PublicKey muunPublicKey = fundingOutput.getMuunPublicKey();

        insertStatement.bind(
                swap.getId() == null ? BaseEntity.NULL_ID : swap.getId(),
                swap.houstonUuid,
                swap.getInvoice(),
                receiver.getAlias(),
                receiver.getSerializedNetworkAddresses(),
                receiver.getPublicKey(),
                fundingOutput.getOutputAddress(),
                fundingOutput.getOutputAmountInSatoshis(),
                fundingOutput.getConfirmationsNeeded(),
                userLockTime != null ? Long.valueOf(userLockTime) : null,
                userRefundAddress.getAddress(),
                userRefundAddress.getDerivationPath(),
                userRefundAddress.getVersion(),
                fundingOutput.getServerPaymentHashInHex(),
                fundingOutput.getServerPublicKeyInHex(),
                swap.getFees().getSweepInSats(),
                swap.getFees().getLightningInSats(),
                swap.getExpiresAt(),
                swap.getPayedAt(),
                swap.getPreimageInHex(),
                swap.getWillPreOpenChannel(),
                swap.getFees().getChannelOpenInSats(),
                swap.getFees().getChannelCloseInSats(),
                fundingOutput.getScriptVersion(),
                expirationInBlocks != null ? Long.valueOf(expirationInBlocks) : null,
                userPublicKey != null ? userPublicKey.serializeBase58() : null,
                userPublicKey != null ? userPublicKey.getAbsoluteDerivationPath() : null,
                muunPublicKey != null ? muunPublicKey.serializeBase58() : null,
                muunPublicKey != null ? muunPublicKey.getAbsoluteDerivationPath() : null,
                swap.getFundingOutput().getDebtType(),
                swap.getFundingOutput().getDebtAmountInSatoshis()
        );

        return insertStatement;
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
        final Long userLockTime = entity.funding_user_lock_time();
        final Long expirationInBlocks = entity.funding_expiration_in_blocks();

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
                        entity.funding_output_debt_type(),
                        entity.funding_output_debt_amount_in_satoshis(),
                        (int) entity.funding_confirmations_needed(),
                        userLockTime != null ? (int) userLockTime.longValue() : null,
                        new MuunAddress(
                                (int) entity.funding_user_refund_address_version(),
                                entity.funding_user_refund_address_path(),
                                entity.funding_user_refund_address()
                        ),
                        entity.funding_server_payment_hash_in_hex(),
                        entity.funding_server_public_key_in_hex(),
                        (int) entity.funding_script_version(),
                        expirationInBlocks != null ? (int) expirationInBlocks.longValue() : null,
                        getUserPublicKey(entity),
                        getMuunPublicKey(entity)
                ),
                new SubmarineSwapFees(
                        entity.lightning_fee_in_satoshis(),
                        entity.sweep_fee_in_satoshis(),
                        entity.channel_open_fee_in_satoshis(),
                        entity.channel_close_fee_in_satoshis()
                ),
                entity.expires_at(),
                entity.will_pre_open_channel(),
                entity.payed_at(),
                entity.preimage_in_hex()
        );
    }

    private static PublicKey getUserPublicKey(SubmarineSwapEntity entity) {
        if (entity.funding_user_public_key() == null) {
            return null;
        }

        return PublicKey.deserializeFromBase58(
                entity.funding_user_public_key_path(),
                entity.funding_user_public_key()
        );
    }

    private static PublicKey getMuunPublicKey(SubmarineSwapEntity entity) {
        if (entity.funding_muun_public_key() == null) {
            return null;
        }

        return PublicKey.deserializeFromBase58(
                entity.funding_muun_public_key_path(),
                entity.funding_muun_public_key()
        );
    }
}
