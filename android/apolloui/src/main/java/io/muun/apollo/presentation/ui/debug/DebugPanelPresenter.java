package io.muun.apollo.presentation.ui.debug;

import io.muun.apollo.domain.action.ContactActions;
import io.muun.apollo.domain.action.OperationActions;
import io.muun.apollo.domain.action.address.CreateAddressAction;
import io.muun.apollo.domain.action.address.SyncExternalAddressIndexesAction;
import io.muun.apollo.domain.action.incoming_swap.GenerateInvoiceAction;
import io.muun.apollo.domain.action.integrity.IntegrityAction;
import io.muun.apollo.domain.action.realtime.FetchRealTimeDataAction;
import io.muun.apollo.domain.selector.UserPreferencesSelector;
import io.muun.apollo.presentation.ui.base.BasePresenter;
import io.muun.apollo.presentation.ui.base.BaseView;
import io.muun.apollo.presentation.ui.base.di.PerActivity;
import io.muun.common.crypto.hd.MuunAddress;

import rx.Observable;
import rx.functions.Action0;

import javax.inject.Inject;

@PerActivity
public class DebugPanelPresenter extends BasePresenter<BaseView> {

    private final OperationActions operationActions;
    private final ContactActions contactActions;
    private final IntegrityAction integrityAction;

    private final FetchRealTimeDataAction fetchRealTimeData;
    private final SyncExternalAddressIndexesAction syncExternalAddressIndexes;
    private final CreateAddressAction createAddress;
    private final GenerateInvoiceAction generateInvoice;
    private final UserPreferencesSelector userPreferencesSel;

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
            CreateAddressAction createAddressAction,
            GenerateInvoiceAction generateInvoiceAction,
            UserPreferencesSelector userPreferencesSel) {

        this.operationActions = operationActions;
        this.contactActions = contactActions;
        this.integrityAction = integrityAction;
        this.syncExternalAddressIndexes = syncExternalAddressIndexes;
        this.fetchRealTimeData = fetchRealTimeData;
        this.createAddress = createAddressAction;
        this.generateInvoice = generateInvoiceAction;
        this.userPreferencesSel = userPreferencesSel;
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
        doInBackground(() -> {
            final MuunAddress segwitAddress = createAddress.actionNow().getSegwit();
            new LappClient().receiveBtc(0.4, segwitAddress.getAddress());
        });
    }

    /**
     * Use Lapp client to send btc to this wallet via LN. Only to be used in Regtest or local build.
     */
    public void fundThisWalletOffChain() {
        doInBackground(() -> {
            final long amountInSats = 11000L;
            final String invoice = generateInvoice.actionNow(amountInSats);
            final boolean turboChannels = !userPreferencesSel.get().getStrictMode();
            new LappClient().receiveBtcViaLN(invoice, amountInSats, turboChannels);
        });
    }

    /**
     * Use Lapp client to generate a block. Only to be used in Regtest or local builds.
     */
    public void generateBlock() {
        doInBackground(() -> new LappClient().generateBlocks(1));
    }

    /**
     * Use Lapp client to drop last tx. Only to be used in Regtest or local builds.
     */
    public void dropLastTxFromMempool() {
        doInBackground(() -> new LappClient().dropLastTxFromMempool());
    }

    /**
     * Use Lapp client to drop last tx. Only to be used in Regtest or local builds.
     */
    public void dropTx(String txId) {
        doInBackground(() -> new LappClient().dropTx(txId));
    }

    /**
     * Use Lapp client to undrop last tx. Only to be used in Regtest or local builds.
     */
    public void undropTx(String txId) {
        doInBackground(() -> new LappClient().undropTx(txId));
    }

    private void doInBackground(Action0 action) {
        final Observable<Void> observable = Observable.defer(() -> {
            action.call();
            return Observable.just(null);
        });

        observable
                .compose(transformerFactory.getAsyncExecutor())
                .subscribe();
    }
}
