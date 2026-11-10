package io.muun.apollo.data.preferences

import android.content.Context
import io.muun.apollo.data.preferences.rx.Preference
import io.muun.apollo.data.serialization.SerializationUtils
import io.muun.apollo.domain.libwallet.LibwalletClient
import io.muun.apollo.domain.model.ExchangeRateWindow
import io.muun.common.Optional
import rx.Observable
import rx.subjects.PublishSubject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton: libwallet's key-value storage emits no change notifications, so stores are
 * announced through an in-process subject that every consumer must share.
 */
// Open for mockito to mock/spy
@Singleton
open class ExchangeRateWindowRepository @Inject constructor(
    context: Context,
    repositoryRegistry: RepositoryRegistry,
    private val libwalletClient: LibwalletClient,
) : BaseRepository(context, repositoryRegistry) {

    companion object {
        private const val KEY_WINDOW = "window"
        private const val KEY_FIXED_WINDOW = "fixed_window"
    }

    private val windowPreference: Preference<String>
        get() = rxSharedPreferences.getString(KEY_WINDOW)

    private val fixedWindowPreference: Preference<String>
        get() = rxSharedPreferences.getString(KEY_FIXED_WINDOW)

    // Stands in for the SharedPreferences change listener: hands each stored window to the
    // current subscribers and retains nothing, so there is no state to reset on logout.
    private val windowSubject = PublishSubject.create<ExchangeRateWindow>()

    override val fileName get() = "exchange_rate_window"

    /**
     * Return true if `fetch()` is safe to call.
     */
    val isSet: Boolean
        get() = read() != null

    /**
     * Fetch an observable instance of the persisted exchange rates: the stored window on
     * subscribe, then every window stored afterwards.
     */
    fun fetch(): Observable<ExchangeRateWindow> =
        windowSubject.startWith(Observable.fromCallable { fetchOne() })
            // SharedPreferences didn't notify when the stored value was unchanged, and Houston
            // hands out the same window to every fetch within the same xrates minute.
            .distinctUntilChanged { it.windowHid }

    /**
     * Get the current exchange rates.
     */
    fun fetchOne(): ExchangeRateWindow =
        checkNotNull(read()) { "No exchange rate window stored" }

    /**
     * Store the current exchange rates. Stores exclude each other so subscribers receive
     * windows in the order they reached storage: a session sync and a forced sync can overlap,
     * and the subject emits what it is handed instead of re-reading storage.
     */
    @Synchronized
    fun storeLatest(exchangeRateWindow: ExchangeRateWindow) {
        // Downgrade insurance: older versions read the window from this preference, and
        // the migration's preserved copy doesn't cover wallets whose first login happened
        // on this version, or that logged out (which clears the preferences file). Deleted
        // with the rest of the native persistence in the cleanup PR.
        windowPreference.set(
            SerializationUtils.serializeJson(ExchangeRateWindow::class.java, exchangeRateWindow)
        )

        try {
            libwalletClient.saveExchangeRateWindow(exchangeRateWindow)
        } catch (error: Throwable) {
            // Log and continue: the next periodic refresh retries the save. Nothing is
            // emitted, so subscribers only see windows that a later read also returns.
            Timber.e(error, "Failed to save exchange rate window in libwallet")
            return
        }

        windowSubject.onNext(exchangeRateWindow)
    }

    /**
     * Store an exchange rate window, that will, temporarily, be fixed for some specific flow.
     */
    fun storeAndFix(exchangeRateWindow: ExchangeRateWindow) {
        fixedWindowPreference.set(
            SerializationUtils.serializeJson(ExchangeRateWindow::class.java, exchangeRateWindow)
        )
    }

    /**
     * Get a fixed exchange rate window, if any.
     */
    val fixedWindow: Optional<ExchangeRateWindow>
        get() = if (fixedWindowPreference.isSet) {
            val json = fixedWindowPreference.get()
            Optional.of(SerializationUtils.deserializeJson(ExchangeRateWindow::class.java, json))
        } else {
            Optional.empty()
        }

    private fun read(): ExchangeRateWindow? =
        try {
            libwalletClient.getExchangeRateWindow()
        } catch (error: Throwable) {
            // Degrade to no window: isSet turns false and the next sync refetches, the same
            // path as a fresh install.
            Timber.e(error, "Failed to read the exchange rate window from libwallet")
            null
        }
}
