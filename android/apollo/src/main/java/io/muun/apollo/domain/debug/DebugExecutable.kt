package io.muun.apollo.domain.debug

import io.muun.apollo.data.debug.LappClient
import io.muun.apollo.data.debug.LappClientError
import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.data.os.execution.ExecutionTransformerFactory
import io.muun.apollo.domain.action.address.CreateAddressAction
import io.muun.apollo.domain.action.incoming_swap.GenerateInvoiceAction
import io.muun.apollo.domain.action.user.UpdateUserPreferencesAction
import io.muun.apollo.domain.errors.debug.DebugExecutableError
import io.muun.apollo.domain.selector.UserPreferencesSelector
import io.muun.common.crypto.hd.MuunAddress
import rx.Observable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TODO: The idea is for this class to actually become an interface and have multiple
 * DebugExecutable impls each for its own functionality.
 */
@Singleton
class DebugExecutable @Inject constructor(
    private val updateUserPreferences: UpdateUserPreferencesAction,
    private val createAddress: CreateAddressAction,
    private val generateInvoice: GenerateInvoiceAction,
    private val userPreferencesSel: UserPreferencesSelector,
    private val houstonClient: HoustonClient,
    private val transformerFactory: ExecutionTransformerFactory,
) {

    private val lapp = LappClient()

    fun fundWalletOnChain(): Observable<Void> = Observable.defer {
        val segwitAddress: MuunAddress = createAddress.actionNow().segwit
        lapp.receiveBtc(0.4, segwitAddress.address)

        Observable.just<Void?>(null)
    }.compose(transformerFactory.getAsyncExecutor())
        .compose(errorMapper())

    fun fundWalletOffChain(): Observable<Void> = Observable.defer {
        val amountInSats = 11000L
        val invoice: String = generateInvoice.actionNow(amountInSats)
        val turboChannels: Boolean = !userPreferencesSel.get().strictMode
        lapp.receiveBtcViaLN(invoice, amountInSats, turboChannels)

        Observable.just<Void?>(null)
    }.compose(transformerFactory.getAsyncExecutor())
        .compose(errorMapper())

    fun generateBlock(): Observable<Void> = Observable.defer {
        lapp.generateBlocks(1)

        Observable.just<Void?>(null)
    }.compose(transformerFactory.getAsyncExecutor())

    fun dropLastTxFromMempool(): Observable<Void> = Observable.defer {
        lapp.dropLastTxFromMempool()

        Observable.just<Void?>(null)
    }.compose(transformerFactory.getAsyncExecutor())
        .compose(errorMapper())

    fun dropTx(txId: String): Observable<Void> = Observable.defer {
        lapp.dropTx(txId)

        Observable.just<Void?>(null)
    }.compose(transformerFactory.getAsyncExecutor())
        .compose(errorMapper())

    fun undropTx(txId: String): Observable<Void> = Observable.defer {
        lapp.undropTx(txId)

        Observable.just<Void?>(null)
    }.compose(transformerFactory.getAsyncExecutor())
        .compose(errorMapper())

    /**
     * Enable/Disable "Multiple sessions" feature for this user.
     */
    fun toggleMultiSession() {
        updateUserPreferences.run { prefs ->
            prefs.copy(allowMultiSession = !prefs.allowMultiSession)
        }
    }

    /**
     * [Only works for "Multiple sessions" users] Expire all user sessions except the current one.
     */
    fun expireAllSessions(): Observable<Void> = Observable.defer {
        houstonClient.expireAllOtherSessions().toObservable<Void>()
    }.compose(transformerFactory.getAsyncExecutor())
        .compose(errorMapper())

    private fun <T> errorMapper() = { observable: Observable<T> ->
        observable.onErrorResumeNext { error: Throwable ->
            if (error is LappClientError) {
                Observable.error(DebugExecutableError(error.message))
            } else {
                Observable.error(error)
            }
        }
    }
}
