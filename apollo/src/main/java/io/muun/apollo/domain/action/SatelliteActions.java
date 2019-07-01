package io.muun.apollo.domain.action;

import io.muun.apollo.data.db.base.ElementNotFoundException;
import io.muun.apollo.data.db.satellite_pairing.SatellitePairingDao;
import io.muun.apollo.data.logging.Logger;
import io.muun.apollo.data.net.ApiObjectsMapper;
import io.muun.apollo.data.net.SatelliteClient;
import io.muun.apollo.data.preferences.SatelliteStateRepository;
import io.muun.apollo.domain.action.base.AsyncAction0;
import io.muun.apollo.domain.action.base.AsyncAction1;
import io.muun.apollo.domain.action.base.AsyncAction4;
import io.muun.apollo.domain.action.base.AsyncActionStore;
import io.muun.apollo.domain.errors.ExpiredSatelliteSession;
import io.muun.apollo.domain.errors.InvalidSatelliteQrCodeError;
import io.muun.apollo.domain.errors.SatelliteAlreadyPairedError;
import io.muun.apollo.domain.errors.SatelliteProtocolNotSupportedError;
import io.muun.apollo.domain.model.HardwareWallet;
import io.muun.apollo.domain.model.PendingWithdrawal;
import io.muun.apollo.domain.model.SatellitePairing;
import io.muun.apollo.domain.satellite.SatelliteProtocol;
import io.muun.apollo.domain.satellite.messages.SatelliteStateMessage;
import io.muun.apollo.domain.satellite.messages.SessionTakeoverMessage;
import io.muun.apollo.domain.satellite.states.BaseSatelliteState;
import io.muun.apollo.domain.satellite.states.SatelliteAddWalletBeginState;
import io.muun.apollo.domain.satellite.states.SatelliteAddWalletEndState;
import io.muun.apollo.domain.satellite.states.SatelliteEmptyState;
import io.muun.apollo.domain.satellite.states.SatelliteScreen;
import io.muun.apollo.domain.satellite.states.SatelliteWithdrawalBeginState;
import io.muun.apollo.domain.satellite.states.SatelliteWithdrawalEndState;
import io.muun.apollo.domain.utils.DateUtils;
import io.muun.common.Optional;
import io.muun.common.bitcoinj.ValidationHelpers;
import io.muun.common.model.HardwareWalletBrand;
import io.muun.common.rx.ObservableFn;
import io.muun.common.rx.RxHelper;

import rx.Observable;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class SatelliteActions {

    private final SatelliteClient satelliteClient;

    private final SatellitePairingDao satellitePairingDao;
    private final SatelliteStateRepository satelliteStateRepository;

    private final HardwareWalletActions hardwareWalletActions;

    public final AsyncAction1<String, SatellitePairing> beginPairingAction;
    public final AsyncAction4<String, String, String, String, Void> completePairingAction;
    public final AsyncAction1<SatellitePairing, Void> expirePairingAction;

    public final AsyncAction1<HardwareWalletBrand, Void> beginAddWalletAction;
    public final AsyncAction1<HardwareWallet, HardwareWallet> endAddWalletAction;
    public final AsyncAction1<HardwareWallet, HardwareWallet> addWalletCompletedSignal;

    public final AsyncAction0<Void> cancelWithdrawalAction;

    public final AsyncAction1<String, Void> resendSatelliteStateAction;
    public final AsyncAction1<String, Void> reportSessionExpiredAction;
    public final AsyncAction1<String, Void> reportSessionTakeoverAction;

    public final AsyncAction0<Void> resetSatelliteStateAction;

    // TODO: remove this. Shouldn't be here. Belongs in data layer.
    public final ApiObjectsMapper mapper;

    /**
     * Constructor.
     */
    @Inject
    public SatelliteActions(SatelliteClient satelliteClient,
                            SatellitePairingDao satellitePairingDao,
                            SatelliteStateRepository satelliteStateRepository,
                            HardwareWalletActions hardwareWalletActions,
                            AsyncActionStore asyncActionStore,
                            ApiObjectsMapper mapper) {

        this.satelliteClient = satelliteClient;
        this.satellitePairingDao = satellitePairingDao;
        this.hardwareWalletActions = hardwareWalletActions;
        this.satelliteStateRepository = satelliteStateRepository;

        this.beginPairingAction = asyncActionStore
                .get("satellite/beginPairing", this::beginPairing);

        this.completePairingAction = asyncActionStore
                .get("satellite/completePairing", this::completePairing);

        this.expirePairingAction = asyncActionStore
                .get("satellite/expirePairing", this::expirePairing);

        this.beginAddWalletAction = asyncActionStore
                .get("satellite/beginHardwareWallet", this::beginAddWallet);

        this.endAddWalletAction = asyncActionStore
                .get("satellite/endAddWallet", this::endAddWallet);

        this.resendSatelliteStateAction = asyncActionStore
                .get("satellite/resendState", this::resendSatelliteState);

        this.reportSessionExpiredAction = asyncActionStore
                .get("satellite/reportSessionExpired", this::reportSessionExpired);

        this.addWalletCompletedSignal = asyncActionStore
                .get("satellite/addWalletCompleted", this::signalAddWalletCompleted);

        this.resetSatelliteStateAction = asyncActionStore
                .get("satellite/resetState", this::resetSatelliteState);

        this.cancelWithdrawalAction = asyncActionStore
                .get("operation/abort-withdrawal", this::cancelWithdrawal);

        this.reportSessionTakeoverAction = asyncActionStore
                .get("satellite/reportSessionTakeover", this::reportSessionTakeover);

        this.mapper = mapper;
    }

    /**
     * Determine whether there are any active satellite pairings.
     */
    public Observable<Boolean> hasActivePairings() {
        return satellitePairingDao.fetchActivePairings()
                .first()
                .map(satellitePairings -> !satellitePairings.isEmpty());
    }

    /**
     * Return the list of active pairings.
     */
    public Observable<List<SatellitePairing>> fetchActivePairings() {
        return satellitePairingDao.fetchActivePairings();
    }

    /**
     * Return an optional SatellitePairing (in any status), matching by apolloSessionUuid.
     */
    public Optional<SatellitePairing> findPairingByApolloSession(String sessionUuid) {
        return satellitePairingDao.fetchByApolloSession(sessionUuid)
                .compose(ObservableFn.onTypedErrorResumeNext(
                        ElementNotFoundException.class,
                        error -> Observable.just(null)
                ))
                .map(Optional::ofNullable)
                .toBlocking()
                .first();
    }

    /**
     * Set the current SatelliteState, and notify all paired satellites.
     */
    public Observable<Void> setSatelliteState(BaseSatelliteState state) {
        return Observable.defer(() -> {
            final SatelliteStateMessage oldState = satelliteStateRepository
                    .getSatelliteState();

            final SatelliteStateMessage newState = state.getStateMessage();
            satelliteStateRepository.setSatelliteState(newState);

            if (oldState.screen == newState.screen && newState.screen == SatelliteScreen.EMPTY) {
                // Both states are EMPTY, do not re-broadcast. Careful: we're not actually saying
                // the state objects are equal, if EMPTY gets new properties this will break.
                return Observable.just(null);
            }

            // The state actually changed. Notify!
            return fetchPairingInUse()
                    .filter(Optional::isPresent)
                    .flatMap(pairing -> sendSatelliteState(pairing.get(), newState));
        });
    }

    private Observable<Void> sendSatelliteState(SatellitePairing pairing,
                                                SatelliteStateMessage stateMessage) {

        return satelliteClient.sendMessage(pairing.satelliteSessionUuid, stateMessage)
                .compose(ObservableFn.onTypedErrorResumeNext(
                        ExpiredSatelliteSession.class,
                        error -> {
                            Logger.error(error);
                            return Observable.just(null);
                        }
                ));
    }

    public Optional<PendingWithdrawal> getPendingWithdrawal() {
        return satelliteStateRepository.getPendingWithdrawal();
    }

    public Observable<Optional<PendingWithdrawal>> watchPendingWithdrawal() {
        return satelliteStateRepository.watchPendingWithdrawal();
    }

    /**
     * Begin the withdrawal process, and notify all paired Satellites.
     */
    public Observable<Void> beginWithdrawal(PendingWithdrawal pendingWithdrawal) {
        return Observable
                .defer(() -> {
                    satelliteStateRepository.setPendingWithdrawal(pendingWithdrawal);

                    return hardwareWalletActions
                            .fetchOne(pendingWithdrawal.hardwareWalletHid)
                            .first();
                })
                .flatMap(wallet -> setSatelliteState(new SatelliteWithdrawalBeginState(
                        pendingWithdrawal.uuid,
                        wallet.brand,
                        wallet.label,
                        pendingWithdrawal.receiverAddress,
                        pendingWithdrawal.amount,
                        hardwareWalletActions.buildWithdrawal(pendingWithdrawal)
                )));
    }

    /**
     * End the withdrawal process, and notify all paired Satellites.
     */
    public Observable<Void> endWithdrawal(PendingWithdrawal pendingWithdrawal) {
        return Observable.defer(() -> {
            satelliteStateRepository.setPendingWithdrawal(pendingWithdrawal);

            return setSatelliteState(new SatelliteWithdrawalEndState(
                pendingWithdrawal.uuid,
                pendingWithdrawal.amount
            ));
        });
    }

    /**
     * Cancel the withdrawal process, and notify all paired Satellites.
     */
    public Observable<Void> cancelWithdrawal() {
        return Observable.defer(() -> {
            satelliteStateRepository.setPendingWithdrawal(null);
            return setSatelliteState(new SatelliteEmptyState());
        });
    }

    private Observable<Void> beginAddWallet(HardwareWalletBrand brand) {
        return setSatelliteState(new SatelliteAddWalletBeginState(brand));
    }

    /**
     * This may look like a stupid idea but it's actually a great way to send data from background
     * (e.g NotificationProcessor) to the front end (e.g Activity or Fragment).
     * In particular, this is used when an "AddHArdwareWallet" notification from satellite is
     * received, to notify the hardware wallet pairing flow.
     */
    private Observable<HardwareWallet> signalAddWalletCompleted(HardwareWallet hardwareWallet) {
        return Observable.just(hardwareWallet);
    }

    private Observable<HardwareWallet> endAddWallet(HardwareWallet walletInfo) {
        return hardwareWalletActions.createOrUpdate(walletInfo)
                .flatMap(wallet ->
                        setSatelliteState(
                                new SatelliteAddWalletEndState(mapper.mapHardwareWallet(wallet))
                        ).map(ignore -> wallet)
                );
    }

    private Observable<Void> resendSatelliteState(String matchByApolloSessionUuid) {
        return satellitePairingDao
                .fetchByApolloSession(matchByApolloSessionUuid)
                .first()
                .flatMap(pairing -> sendSatelliteState(
                        pairing,
                        satelliteStateRepository.getSatelliteState()
                ))
                .doOnError(Logger::error);
    }

    public Observable<List<SatellitePairing>> getCompletedPairings() {
        return satellitePairingDao.fetchActivePairings();
    }

    private Observable<SatellitePairing> beginPairing(String qrCodeString) {
        return Observable.defer(() -> {
            final String satelliteSessionUuid = readSatelliteSessionFromQr(qrCodeString);

            if (isAlreadyPaired(satelliteSessionUuid)) {
                throw new SatelliteAlreadyPairedError();
            }

            return satelliteClient.beginPairing(satelliteSessionUuid)
                    .flatMap(satellitePairingDao::store);
        });
    }

    private Observable<Void> completePairing(String matchByApolloSessionUuid,
                                             String browser,
                                             String osVersion,
                                             String ip) {

        return Observable.defer(() -> {
            satellitePairingDao.completePairing(
                    matchByApolloSessionUuid,
                    browser,
                    osVersion,
                    ip,
                    DateUtils.now()
            );

            return resendSatelliteState(matchByApolloSessionUuid)
                    .flatMap(ignored -> reportSessionTakeover(matchByApolloSessionUuid));
        });
    }

    private boolean isAlreadyPaired(String satelliteSessionUuid) {
        return satellitePairingDao.fetchBySatelliteSession(satelliteSessionUuid)
                .map(pairing -> true)
                .compose(ObservableFn.onTypedErrorResumeNext(
                        ElementNotFoundException.class,
                        error -> Observable.just(false)
                ))
                .toBlocking()
                .first();
    }

    private Observable<Void> expirePairing(SatellitePairing pairing) {
        return satelliteClient.expirePairing(pairing)
                .doOnNext(i -> satellitePairingDao.expirePairing(pairing.satelliteSessionUuid));
    }

    private Observable<Void> reportSessionExpired(String satelliteSessionUuid) {
        return satellitePairingDao.fetchBySatelliteSession(satelliteSessionUuid)
                .first()
                .flatMap(this::expirePairing);
    }

    private Observable<Void> resetSatelliteState() {
        return setSatelliteState(new SatelliteEmptyState());
    }

    private Observable<Void> reportSessionTakeover(String apolloSessionUuid) {
        return setPairingInUse(apolloSessionUuid)
                .flatMap(this::sendTakeoverMessages);

    }

    private Observable<SatellitePairing> setPairingInUse(String apolloSessionUuid) {
        return satellitePairingDao.fetchByApolloSession(apolloSessionUuid)
                .first()
                .doOnNext(satellitePairingDao::setPairingInUse);
    }


    private Observable<Optional<SatellitePairing>> fetchPairingInUse() {
        return satellitePairingDao.fetchPairingInUse()
                .first()
                .map(Optional::of)
                .compose(ObservableFn.onTypedErrorResumeNext(
                        ElementNotFoundException.class,
                        error -> Observable.just(Optional.empty())
                ));
    }

    private Observable<Void> sendTakeoverMessages(SatellitePairing currentPairing) {
        final SessionTakeoverMessage msg = new SessionTakeoverMessage();

        return satellitePairingDao
                .fetchActivePairings()
                .first()
                .flatMap(Observable::from)
                .filter(pairing -> !pairing.equals(currentPairing))
                .flatMap(pairing -> satelliteClient.sendMessage(pairing.satelliteSessionUuid, msg))
                .lastOrDefault(null)
                .map(RxHelper::toVoid);
    }

    private String readSatelliteSessionFromQr(String qrCodeString) {
        final String[] parts = qrCodeString.split("\\$");

        final int version;
        final String satelliteSessionUuid;

        try {
            version = Integer.parseInt(parts[0]);
            satelliteSessionUuid = parts[1];

        } catch (IndexOutOfBoundsException ex) {
            throw new InvalidSatelliteQrCodeError(qrCodeString, ex);
        }

        if (! ValidationHelpers.isValidUuid(satelliteSessionUuid)) {
            throw new InvalidSatelliteQrCodeError(qrCodeString);
        }

        if (version != SatelliteProtocol.VERSION) {
            // We don't support this version.
            throw new SatelliteProtocolNotSupportedError();
        }

        return satelliteSessionUuid;
    }
}
