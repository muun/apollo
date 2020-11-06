package io.muun.apollo.data.db.incoming_swap;

import io.muun.apollo.data.db.base.BaseEntity;
import io.muun.apollo.data.db.operation.IncomingSwapModel;
import io.muun.apollo.domain.model.IncomingSwap;
import io.muun.common.utils.Encodings;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.sqlite.db.SupportSQLiteDatabase;
import com.google.auto.value.AutoValue;
import com.squareup.sqldelight.prerelease.SqlDelightStatement;

@AutoValue
public abstract class IncomingSwapEntity implements IncomingSwapModel, BaseEntity {

    public static final Factory<IncomingSwapEntity>
            FACTORY = new IncomingSwapModel.Factory<>(
            AutoValue_IncomingSwapEntity::new
    );

    @AutoValue
    public abstract static class CompleteIncomingSwap implements IncomingSwapModel.SelectAllModel<
            IncomingSwapEntity,
            IncomingSwapHtlcEntity> {
    }

    /**
     * Map from the model to the content values.
     */
    public static SqlDelightStatement fromModel(SupportSQLiteDatabase db, IncomingSwap swap) {

        final IncomingSwapModel.InsertIncomingSwap insertStatement =
                new IncomingSwapModel.InsertIncomingSwap(db);

        final byte[] sphinxPacket = swap.getSphinxPacket();

        insertStatement.bind(
                swap.getId() == null ? BaseEntity.NULL_ID : swap.getId(),
                swap.houstonUuid,
                Encodings.bytesToHex(swap.getPaymentHash()),
                sphinxPacket != null ? Encodings.bytesToHex(sphinxPacket) : null,
                swap.getHtlc().houstonUuid
        );

        return insertStatement;
    }

    /**
     * Map from the database cursor to the model.
     */
    public static IncomingSwap toModel(Cursor cursor) {
        final CompleteIncomingSwap entity = FACTORY.selectAllMapper(
                AutoValue_IncomingSwapEntity_CompleteIncomingSwap::new,
                IncomingSwapHtlcEntity.FACTORY
        ).map(cursor);

        return getIncomingSwap(entity.swap(), entity.htlc());
    }

    /**
     * Builds a IncomingSwap domain layer model from a data layer IncomingSwapEntity.
     */
    @NonNull
    public static IncomingSwap getIncomingSwap(IncomingSwapEntity entity,
                                               IncomingSwapHtlcEntity htlc) {

        final String sphinxPacketInHex = entity.sphinx_packet_in_hex();
        return new IncomingSwap(
                entity.id(),
                entity.houston_uuid(),
                Encodings.hexToBytes(entity.payment_hash_in_hex()),
                IncomingSwapHtlcEntity.getIncomingSwapHtlc(htlc),
                sphinxPacketInHex != null ? Encodings.hexToBytes(sphinxPacketInHex) : null
        );
    }

}
