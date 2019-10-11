package io.muun.apollo.data.db.satellite_pairing;

import io.muun.apollo.data.db.base.BaseDao;
import io.muun.apollo.domain.model.SatellitePairing;

import org.threeten.bp.ZonedDateTime;
import rx.Observable;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import static io.muun.apollo.domain.model.SatellitePairing.Status.COMPLETE;

@Singleton
public class SatellitePairingDao extends BaseDao<SatellitePairing> {

    /**
     * Constructor.
     */
    @Inject
    protected SatellitePairingDao() {
        super(
                SatellitePairingEntity.CREATE_TABLE,
                SatellitePairingEntity::fromModel,
                SatellitePairingEntity::toModel,
                SatellitePairingEntity.TABLE_NAME
        );
    }

    /**
     * Fetches all active pairings from the db.
     */
    public Observable<List<SatellitePairing>> fetchActivePairings() {
        return fetchList(SatellitePairingEntity.FACTORY.selectActivePairings());
    }

    /**
     * Fetches the currently in use pairing from the db.
     */
    public Observable<SatellitePairing> fetchPairingInUse() {
        return fetchOneOrFail(SatellitePairingEntity.FACTORY.selectPairingInUse());
    }

    /**
     * Updates the currently in use pairing in the db.
     */
    public void setPairingInUse(SatellitePairing pairing) {
        final SatellitePairingModel.SetPairingInUse statement = new SatellitePairingEntity
                .SetPairingInUse(db);

        statement.bind(pairing.getId());

        executeUpdate(statement);
    }

    /**
     * Complete pairing process by updating pairing status to COMPLETE and saving satellite/browser
     * information.
     */
    public void completePairing(String apolloSessionUuid,
                                String browser,
                                String osVersion,
                                String ip,
                                ZonedDateTime lastActive) {

        final SatellitePairingModel.UpdateStatus statement = new SatellitePairingEntity
                .UpdateStatus(db, SatellitePairingEntity.FACTORY);

        statement.bind(COMPLETE, browser, osVersion, ip, lastActive, apolloSessionUuid);

        executeUpdate(statement);
    }

    /**
     * Fetch the pairing associated with a satelliteSessionUuid.
     */
    public Observable<SatellitePairing> fetchBySatelliteSession(String satelliteSessionUuid) {
        return fetchOneOrFail(
                SatellitePairingEntity.FACTORY.selectBySatelliteSessionUuid(satelliteSessionUuid)
        );
    }

    /**
     * Fetch the pairing associated with an apolloSessionUuid.
     */
    public Observable<SatellitePairing> fetchByApolloSession(String apolloSessionUuid) {
        return fetchOneOrFail(
                SatellitePairingEntity.FACTORY.selectByApolloSessionUuid(apolloSessionUuid)
        );
    }

    /**
     * Fetch the pairing associated with a satelliteSessionUuid.
     */
    public void expirePairing(String satelliteSessionUuid) {
        final SatellitePairingModel.ExpirePairing statement = new SatellitePairingEntity
                .ExpirePairing(db);

        statement.bind(satelliteSessionUuid);

        executeUpdate(statement);
    }
}
