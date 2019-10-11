package io.muun.apollo.data.db.hwallet;

import io.muun.apollo.data.db.base.BaseEntity;
import io.muun.apollo.domain.model.HardwareWallet;
import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.model.HardwareWalletBrand;

import android.database.Cursor;
import androidx.sqlite.db.SupportSQLiteDatabase;
import com.google.auto.value.AutoValue;
import com.squareup.sqldelight.prerelease.EnumColumnAdapter;
import com.squareup.sqldelight.prerelease.SqlDelightStatement;

@AutoValue
public abstract class HardwareWalletEntity implements HardwareWalletModel, BaseEntity {

    public static final Factory<HardwareWalletEntity> FACTORY =
            new HardwareWalletModel.Factory<>(
                    AutoValue_HardwareWalletEntity::new,
                    EnumColumnAdapter.create(HardwareWalletBrand.class),
                    ZONED_DATE_TIME_ADAPTER,
                    ZONED_DATE_TIME_ADAPTER
            );

    /**
     * Map from the model to the content values.
     */
    public static SqlDelightStatement fromModel(SupportSQLiteDatabase db, HardwareWallet wallet) {

        final HardwareWalletModel.InsertHardwareWallet insertStatement = new HardwareWalletModel
                .InsertHardwareWallet(db, FACTORY);

        insertStatement.bind(
                wallet.getId() == null ? BaseEntity.NULL_ID : wallet.getId(),
                wallet.getHid(),
                wallet.getBrand(),
                wallet.getModel(),
                wallet.getLabel(),
                wallet.getBasePublicKey().serializeBase58(),
                wallet.getBasePublicKey().getAbsoluteDerivationPath(),
                wallet.getCreatedAt(),
                wallet.getLastPairedAt(),
                wallet.isPaired()
        );

        return insertStatement;
    }

    /**
     * Map from the database cursor to the model.
     */
    public static HardwareWallet toModel(Cursor cursor) {

        final HardwareWalletEntity entity = FACTORY.selectAllMapper().map(cursor);

        return new HardwareWallet(
                entity.id(),
                entity.hid(),
                entity.brand(),
                entity.model(),
                entity.label(),
                PublicKey.deserializeFromBase58(
                        entity.base_public_key_path(),
                        entity.base_public_key()
                ),
                entity.created_at(),
                entity.last_paired_at(),
                entity.is_paired()
        );
    }
}
