package io.muun.apollo.presentation.ui.debug;

import io.muun.apollo.domain.action.ContactActions;
import io.muun.apollo.domain.action.OperationActions;
import io.muun.apollo.domain.action.address.SyncExternalAddressIndexesAction;
import io.muun.apollo.domain.action.integrity.IntegrityAction;
import io.muun.apollo.domain.action.realtime.FetchRealTimeDataAction;
import io.muun.apollo.domain.debug.DebugExecutable;
import io.muun.apollo.domain.errors.debug.DebugExecutableError;
import io.muun.apollo.presentation.ui.base.BasePresenter;
import io.muun.apollo.presentation.ui.base.BaseView;
import io.muun.apollo.presentation.ui.base.di.PerActivity;

import rx.Observable;
import rx.functions.Actions;
import timber.log.Timber;

import javax.inject.Inject;

@PerActivity
public class DebugPanelPresenter extends BasePresenter<BaseView> {

    private final OperationActions operationActions;
    private final ContactActions contactActions;
    private final IntegrityAction integrityAction;

    private final FetchRealTimeDataAction fetchRealTimeData;
    private final SyncExternalAddressIndexesAction syncExternalAddressIndexes;
    private final DebugExecutable debugExecutable;

    /**
     * Creates a presenter.
     */
    @Inject
    public DebugPanelPresenter(
            OperationActions operationActions,
            ContactActions contactActions,
            IntegrityAction integrityAction,
            SyncExternalAddressIndexesAction syncExternalAddressIndexes,
            FetchRealTimeDataAction fetchRealTimeData,
            DebugExecutable debugExecutable
    ) {

        this.operationActions = operationActions;
        this.contactActions = contactActions;
        this.integrityAction = integrityAction;
        this.syncExternalAddressIndexes = syncExternalAddressIndexes;
        this.fetchRealTimeData = fetchRealTimeData;
        this.debugExecutable = debugExecutable;
    }

    /**
     * Syncs operations.
     */
    public void fetchReplaceOperations() {

        view.showTextToast("Re-downloading operations...");

        final Observable<Void> observable = operationActions
                .fetchReplaceOperations()
                .compose(getAsyncExecutor())
                .doOnNext(ignored -> view.showTextToast("Done"));

        subscribeTo(observable);
    }

    /**
     * Syncs contacts.
     */
    public void fetchReplaceContacts() {

        view.showTextToast("Re-downloading contacts...");

        final Observable<Void> observable = contactActions
                .fetchReplaceContacts()
                .compose(getAsyncExecutor())
                .doOnNext(ignored -> view.showTextToast("Done"));

        subscribeTo(observable);
    }

    /**
     * Syncs phone contacts.
     */
    public void scanReplacePhoneContacts() {

        view.showTextToast("Re-scanning phone contacts...");

        final Observable<Void> observable = contactActions
                .resetSyncPhoneContacts()
                .compose(getAsyncExecutor())
                .doOnNext(ignored -> view.showTextToast("Done"));

        subscribeTo(observable);
    }

    /**
     * Syncs real-time data.
     */
    public void syncRealTimeData() {
        view.showTextToast("Syncing real-time data...");

        final Observable<Void> observable = fetchRealTimeData
                .action()
                .compose(getAsyncExecutor())
                .doOnNext(ignored -> view.showTextToast("Real-time data synced"));

        subscribeTo(observable);
    }

    /**
     * Syncs external addresses indexes.
     */
    public void syncExternalAddressesIndexes() {

        view.showTextToast("Syncing external addresses indexes...");

        final Observable<Void> observable = syncExternalAddressIndexes
                .action()
                .compose(getAsyncExecutor())
                .doOnNext(ignored -> view.showTextToast("External addresses indexes synced"));

        subscribeTo(observable);
    }

    /**
     * Perform a background integrity check with Houston.
     */
    public void checkIntegrity() {
        final Observable<Void> observable = integrityAction.checkIntegrity()
                .compose(getAsyncExecutor())
                .doOnNext(ignored -> view.showTextToast("Integrity check complete, see log"));

        subscribeTo(observable);
    }

    /**
     * Force a call to update the FCM token, using the one already present on the preferences.
     */
    public void updateFcmToken() {
        throw new RuntimeException("This method broke through several layers and nobody used it.");
        //updateFcmTokenAction.run(userRepository.getFcmToken().get());
    }

    /**
     * Use Lapp client to send btc to this wallet. Only to be used in Regtest or local build.
     */
    public void fundThisWalletOnChain() {
        debugExecutable.fundWalletOnChain()
                .subscribe(Actions.empty(), this::handleError);
    }

    /**
     * Use Lapp client to send btc to this wallet via LN. Only to be used in Regtest or local build.
     */
    public void fundThisWalletOffChain() {
        debugExecutable.fundWalletOffChain()
                .subscribe(Actions.empty(), this::handleError);
    }

    /**
     * Use Lapp client to generate a block. Only to be used in Regtest or local builds.
     */
    public void generateBlock() {
        debugExecutable.generateBlock()
                .subscribe(Actions.empty(), this::handleError);
    }

    /**
     * Use Lapp client to drop last tx. Only to be used in Regtest or local builds.
     */
    public void dropLastTxFromMempool() {
        debugExecutable.dropLastTxFromMempool()
                .subscribe(Actions.empty(), this::handleError);
    }

    /**
     * Use Lapp client to drop last tx. Only to be used in Regtest or local builds.
     */
    public void dropTx(String txId) {
        debugExecutable.dropTx(txId)
                .subscribe(Actions.empty(), this::handleError);
    }

    /**
     * Use Lapp client to undrop last tx. Only to be used in Regtest or local builds.
     */
    public void undropTx(String txId) {
        debugExecutable.undropTx(txId)
                .subscribe(Actions.empty(), this::handleError);
    }

    @Override
    public void handleError(Throwable error) {
        if (error instanceof DebugExecutableError) {
            Timber.e(error);
            view.showTextToast(error.getMessage());
        } else {
            super.handleError(error);
        }
    }
}
