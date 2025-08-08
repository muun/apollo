package io.muun.apollo.presentation.ui.debug

import io.muun.apollo.data.nfc.api.NfcSession
import io.muun.apollo.domain.action.ContactActions
import io.muun.apollo.domain.action.OperationActions
import io.muun.apollo.domain.action.address.SyncExternalAddressIndexesAction
import io.muun.apollo.domain.action.integrity.IntegrityAction
import io.muun.apollo.domain.action.realtime.FetchRealTimeDataAction
import io.muun.apollo.domain.debug.DebugExecutable
import io.muun.apollo.domain.errors.debug.DebugExecutableError
import io.muun.apollo.presentation.ui.base.BasePresenter
import io.muun.apollo.presentation.ui.base.BaseView
import io.muun.apollo.presentation.ui.base.di.PerActivity
import rx.functions.Action1
import rx.functions.Actions
import timber.log.Timber
import javax.inject.Inject

@PerActivity
class DebugPanelPresenter @Inject constructor(
    private val operationActions: OperationActions,
    private val contactActions: ContactActions,
    private val integrityAction: IntegrityAction,
    private val syncExternalAddressIndexes: SyncExternalAddressIndexesAction,
    private val fetchRealTimeData: FetchRealTimeDataAction,
    private val debugExecutable: DebugExecutable,
) : BasePresenter<BaseView>() {

    @Suppress("INACCESSIBLE_TYPE")
    val empty = Actions.empty<Void, Any, Any, Any, Any, Any, Any, Any, Any>() as Action1<Void>

    /**
     * Syncs operations.
     */
    fun fetchReplaceOperations() {
        view.showTextToast("Re-downloading operations...")

        val observable = operationActions
            .fetchReplaceOperations()
            .compose(getAsyncExecutor())
            .doOnNext { view.showTextToast("Done") }

        subscribeTo(observable)
    }

    /**
     * Syncs contacts.
     */
    fun fetchReplaceContacts() {
        view.showTextToast("Re-downloading contacts...")

        val observable = contactActions
            .fetchReplaceContacts()
            .compose(getAsyncExecutor())
            .doOnNext { view.showTextToast("Done") }

        subscribeTo(observable)
    }

    /**
     * Syncs phone contacts.
     */
    fun scanReplacePhoneContacts() {
        view.showTextToast("Re-scanning phone contacts...")

        val observable = contactActions
            .resetSyncPhoneContacts()
            .compose(getAsyncExecutor())
            .doOnNext { view.showTextToast("Done") }

        subscribeTo(observable)
    }

    /**
     * Syncs real-time data.
     */
    fun syncRealTimeData() {
        view.showTextToast("Syncing real-time data...")

        val observable = fetchRealTimeData
            .action()
            .compose(getAsyncExecutor())
            .doOnNext { view.showTextToast("Real-time data synced") }

        subscribeTo(observable)
    }

    /**
     * Syncs external addresses indexes.
     */
    fun syncExternalAddressesIndexes() {
        view.showTextToast("Syncing external addresses indexes...")

        val observable = syncExternalAddressIndexes
            .action()
            .compose(getAsyncExecutor())
            .doOnNext { view.showTextToast("External addresses indexes synced") }

        subscribeTo(observable)
    }

    /**
     * Perform a background integrity check with Houston.
     */
    fun checkIntegrity() {
        val observable = integrityAction.checkIntegrity()
            .compose(getAsyncExecutor())
            .doOnNext { view.showTextToast("Integrity check complete, see log") }

        subscribeTo(observable)
    }

    /**
     * Force a call to update the FCM token, using the one already present on the preferences.
     */
    fun updateFcmToken() {
        throw RuntimeException("This method broke through several layers and nobody used it.")
        //updateFcmTokenAction.run(userRepository.getFcmToken().get());
    }

    /**
     * Use Lapp client to send btc to this wallet. Only to be used in Regtest or local build.
     */
    fun fundThisWalletOnChain() {
        debugExecutable.fundWalletOnChain()
            .subscribe(empty) { error: Throwable ->
                this.handleError(error)
            }
    }

    /**
     * Use Lapp client to send btc to this wallet via LN. Only to be used in Regtest or local build.
     */
    fun fundThisWalletOffChain() {
        debugExecutable.fundWalletOffChain()
            .subscribe(empty) { error: Throwable ->
                this.handleError(error)
            }
    }

    /**
     * Use Lapp client to generate a block. Only to be used in Regtest or local builds.
     */
    fun generateBlock() {
        debugExecutable.generateBlock()
            .subscribe(empty) { error: Throwable ->
                this.handleError(error)
            }
    }

    /**
     * Use Lapp client to drop last tx. Only to be used in Regtest or local builds.
     */
    fun dropLastTxFromMempool() {
        debugExecutable.dropLastTxFromMempool()
            .subscribe(empty) { error: Throwable ->
                this.handleError(error)
            }
    }

    /**
     * Use Lapp client to drop last tx. Only to be used in Regtest or local builds.
     */
    fun dropTx(txId: String) {
        debugExecutable.dropTx(txId)
            .subscribe(empty) { error: Throwable ->
                this.handleError(error)
            }
    }

    /**
     * Use Lapp client to undrop last tx. Only to be used in Regtest or local builds.
     */
    fun undropTx(txId: String) {
        debugExecutable.undropTx(txId)
            .subscribe(empty) { error: Throwable ->
                this.handleError(error)
            }
    }

    /**
     * Pair/Set up a security card to use with this user/wallet. Only to be used in local builds.
     */
    fun setUpSecurityCard(nfcSession: NfcSession) {
        debugExecutable.setUpSecurityCard(nfcSession)
    }

    /**
     * Reset a security card to enable re-use. Only to be used in local builds.
     */
    fun resetSecuritytCard(nfcSession: NfcSession) {
        debugExecutable.resetSecurityCard(nfcSession)
    }

    /**
     * Navigates to the 'diagnostic' activity
     */
    fun enterDiagnosticMode() {
        navigator.navigateToDiagnosticMode(context)
    }

    /**
     * Enable/Disable "Multiple sessions" feature for this user.
     */
    fun toggleMultiSessions() {
        debugExecutable.toggleMultiSession()
    }

    /**
     * [Only works for "Multiple sessions" users] Expire all user sessions except the current one.
     */
    fun expireAllSessions() {
        debugExecutable.expireAllSessions()
            .subscribe(empty) { error: Throwable ->
                this.handleError(error)
            }
    }

    override fun handleError(error: Throwable) {
        if (error is DebugExecutableError) {
            Timber.e(error)
            view.showTextToast(error.message)

        } else {
            super.handleError(error)
        }
    }
}
