package io.muun.apollo.data.db.incoming_swap;

import io.muun.apollo.data.db.base.BaseEntity;
import io.muun.apollo.data.db.operation.IncomingSwapHtlcModel;
import io.muun.apollo.domain.model.IncomingSwapHtlc;
import io.muun.common.utils.Encodings;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.sqlite.db.SupportSQLiteDatabase;
import com.google.auto.value.AutoValue;
import com.squareup.sqldelight.prerelease.SqlDelightStatement;

@AutoValue
public abstract class IncomingSwapHtlcEntity implements IncomingSwapHtlcModel, BaseEntity {

    public static final Factory<IncomingSwapHtlcEntity>
            FACTORY = new Factory<>(AutoValue_IncomingSwapHtlcEntity::new);

    /**
     * Map from the model to the content values.
     */
    public static SqlDelightStatement fromModel(SupportSQLiteDatabase db,
                                                IncomingSwapHtlcDb model) {

        final InsertIncomingSwapHtlc insertStatement = new InsertIncomingSwapHtlc(db);

        final IncomingSwapHtlc htlc = model.getHtlc();
        final byte[] fulfillmentTx = htlc.getFulfillmentTx();
        insertStatement.bind(
                htlc.getId() == null ? BaseEntity.NULL_ID : htlc.getId(),
                htlc.houstonUuid,
                htlc.getExpirationHeight(),
                htlc.getFulfillmentFeeSubsidyInSats(),
                htlc.getLentInSats(),
                Encodings.bytesToHex(htlc.getSwapServerPublicKey()),
                fulfillmentTx != null ? Encodings.bytesToHex(fulfillmentTx) : null,
                htlc.getAddress(),
                htlc.getOutputAmountInSatoshis(),
                Encodings.bytesToHex(htlc.getHtlcTx()),
                model.getSwapHoustonUuid()
        );

        return insertStatement;
    }

    /**
     * Map from the database cursor to the model.
     */
    public static IncomingSwapHtlcDb toModel(Cursor cursor) {
        final IncomingSwapHtlcEntity entity = FACTORY.selectAllMapper().map(cursor);

        return new IncomingSwapHtlcDb(
                entity.incoming_swap_houston_uuid(),
                getIncomingSwapHtlc(entity)
        );
    }

    /**
     * Builds a IncomingSwapHtlc domain layer model from a data layer IncomingSwapHtlcEntity.
     */
    @NonNull
    public static IncomingSwapHtlc getIncomingSwapHtlc(IncomingSwapHtlcEntity entity) {

        final String fulfillmentTxInHex = entity.fulfillment_tx_in_hex();
        return new IncomingSwapHtlc(
                entity.id(),
                entity.houston_uuid(),
                entity.expiration_height(),
                entity.fulfillment_fee_subsidy_in_satoshis(),
                entity.lent_in_satoshis(),
                Encodings.hexToBytes(entity.swap_server_public_key_in_hex()),
                fulfillmentTxInHex != null ? Encodings.hexToBytes(fulfillmentTxInHex) : null,
                entity.address(),
                entity.output_amount_in_satoshis(),
                Encodings.hexToBytes(entity.htlc_tx_in_hex())
        );
    }

}
