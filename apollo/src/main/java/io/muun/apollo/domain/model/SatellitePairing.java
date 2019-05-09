package io.muun.apollo.domain.model;

import io.muun.apollo.domain.model.base.PersistentModel;

import android.support.annotation.Nullable;
import org.threeten.bp.ZonedDateTime;

import javax.validation.constraints.NotNull;

public class SatellitePairing extends PersistentModel {

    @NotNull
    public final String satelliteSessionUuid;

    @NotNull
    public final String apolloSessionUuid;

    @Nullable
    public final String encryptionKey;

    @NotNull
    public final Status status;

    @Nullable
    public final String browser;

    @Nullable
    public final String osVersion;

    @Nullable
    public final String ip;

    @NotNull
    public final ZonedDateTime creationDate;

    @NotNull
    public final ZonedDateTime lastActive;

    @NotNull
    public final Boolean isInUse;

    /**
     * Partial constructor, when creating a new pairing.
     */
    public static SatellitePairing createWaitingForPeer(String satelliteSessionUuid,
                                                        String apolloSessionUuid) {

        final ZonedDateTime now = ZonedDateTime.now();

        return new SatellitePairing(
                null,
                satelliteSessionUuid,
                apolloSessionUuid,
                null, // NOTE: not currently in use, but we can avoid a migration.
                SatellitePairing.Status.WAIT_FOR_PEER,
                null,
                null,
                null,
                now,
                now,
                false
        );
    }

    /**
     * Full constructor.
     */
    public SatellitePairing(
            @Nullable Long id,
            String satelliteSessionUuid,
            String apolloSessionUuid,
            String encryptionKey,
            Status status,
            String browser,
            String osVersion,
            String ip,
            ZonedDateTime creationDate,
            ZonedDateTime lastActive,
            Boolean isInUse) {

        super(id);
        this.satelliteSessionUuid = satelliteSessionUuid;
        this.apolloSessionUuid = apolloSessionUuid;
        this.encryptionKey = encryptionKey;
        this.status = status;
        this.browser = browser;
        this.osVersion = osVersion;
        this.ip = ip;
        this.creationDate = creationDate;
        this.lastActive = lastActive;
        this.isInUse = isInUse;
    }

    public boolean isExpired() {
        return status == Status.EXPIRED;
    }

    /**
     * NOTE: apollo won't notice change to COMPLETE until first receival of Satellite notification.
     */
    public enum Status {
        WAIT_FOR_PEER,
        COMPLETE,
        EXPIRED
    }
}
