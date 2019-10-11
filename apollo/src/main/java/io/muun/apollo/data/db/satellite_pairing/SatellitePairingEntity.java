package io.muun.apollo.data.db.satellite_pairing;

import io.muun.apollo.data.db.base.BaseEntity;
import io.muun.apollo.domain.model.SatellitePairing;

import android.database.Cursor;
import androidx.sqlite.db.SupportSQLiteDatabase;
import com.google.auto.value.AutoValue;
import com.squareup.sqldelight.prerelease.ColumnAdapter;
import com.squareup.sqldelight.prerelease.EnumColumnAdapter;
import com.squareup.sqldelight.prerelease.SqlDelightStatement;

@AutoValue
public abstract class SatellitePairingEntity implements SatellitePairingModel, BaseEntity {

    private static final ColumnAdapter<SatellitePairing.Status, String> PAIRING_STATUS_ADAPTER =
            EnumColumnAdapter.create(SatellitePairing.Status.class);

    public static final Factory<SatellitePairingEntity> FACTORY = new Factory<>(
            AutoValue_SatellitePairingEntity::new,
            PAIRING_STATUS_ADAPTER,
            ZONED_DATE_TIME_ADAPTER,
            ZONED_DATE_TIME_ADAPTER
    );

    /**
     * Map from the model to the content values.
     */
    public static SqlDelightStatement fromModel(SupportSQLiteDatabase db,
                                                SatellitePairing pairing) {

        final SatellitePairingModel.InsertPairing insertStatement = new SatellitePairingModel
                .InsertPairing(db, FACTORY);

        insertStatement.bind(
                pairing.getId() == null ? NULL_ID : pairing.getId(),
                pairing.satelliteSessionUuid,
                pairing.apolloSessionUuid,
                pairing.status,
                pairing.browser,
                pairing.osVersion,
                pairing.ip,
                pairing.creationDate,
                pairing.lastActive,
                pairing.encryptionKey,
                pairing.isInUse
        );

        return insertStatement;
    }

    /**
     * Map from the database cursor to the model.
     */
    public static SatellitePairing toModel(Cursor cursor) {
        final SatellitePairingEntity entity = FACTORY.selectAllMapper().map(cursor);

        return new SatellitePairing(
                entity.id(),
                entity.satellite_session_uuid(),
                entity.apollo_session_uuid(),
                entity.encryption_key(),
                entity.status(),
                entity.browser(),
                entity.os_version(),
                entity.ip(),
                entity.creation_date(),
                entity.last_active(),
                entity.is_in_use()
        );
    }
}
