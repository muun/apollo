package io.muun.apollo.data.db.satellite_pairing;

import io.muun.apollo.data.db.base.BaseEntity;
import io.muun.apollo.domain.model.SatellitePairing;

import android.content.ContentValues;
import android.database.Cursor;
import com.google.auto.value.AutoValue;
import com.squareup.sqldelight.ColumnAdapter;
import com.squareup.sqldelight.EnumColumnAdapter;

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
    public static ContentValues fromModel(SatellitePairing pairing) {

        return FACTORY.marshal()
                .id(pairing.id == null ? NULL_ID : pairing.id)
                .satellite_session_uuid(pairing.satelliteSessionUuid)
                .apollo_session_uuid(pairing.apolloSessionUuid)
                .status(pairing.status)
                .browser(pairing.browser)
                .os_version(pairing.osVersion)
                .ip(pairing.ip)
                .creation_date(pairing.creationDate)
                .last_active(pairing.lastActive)
                .encryption_key(pairing.encryptionKey)
                .is_in_use(pairing.isInUse)
                .asContentValues();
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
