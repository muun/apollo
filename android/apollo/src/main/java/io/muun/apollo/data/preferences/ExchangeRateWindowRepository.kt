package io.muun.apollo.data.preferences

import android.content.Context
import io.muun.apollo.data.preferences.rx.Preference
import io.muun.apollo.data.serialization.SerializationUtils
import io.muun.apollo.domain.model.ExchangeRateWindow
import io.muun.common.Optional
import rx.Observable
import javax.inject.Inject

// Open for mockito to mock/spy
open class ExchangeRateWindowRepository @Inject constructor(
    context: Context,
    repositoryRegistry: RepositoryRegistry,
) : BaseRepository(context, repositoryRegistry) {

    companion object {
        private const val KEY_WINDOW = "window"
        private const val KEY_FIXED_WINDOW = "fixed_window"
    }

    private val windowPreference: Preference<String>
        get() = rxSharedPreferences.getString(KEY_WINDOW)
    private val fixedWindowPreference: Preference<String>
        get() = rxSharedPreferences.getString(KEY_FIXED_WINDOW)

    override val fileName get() = "exchange_rate_window"

    /**
     * Return true if `fetch()` is safe to call.
     */
    val isSet: Boolean
        get() = windowPreference.isSet

    /**
     * Fetch an observable instance of the persisted exchange rates.
     */
    fun fetch(): Observable<ExchangeRateWindow> {
        return windowPreference.asObservable()
            .map { str: String? ->
                SerializationUtils.deserializeJson(
                    ExchangeRateWindow::class.java, str
                )
            }
    }

    /**
     * Get the current exchange rates.
     */
    fun fetchOne(): ExchangeRateWindow {
        return fetch().toBlocking().first()
    }

    /**
     * Store the current exchange rates.
     */
    fun storeLatest(exchangeRateWindow: ExchangeRateWindow) {
        windowPreference.set(
            SerializationUtils.serializeJson(ExchangeRateWindow::class.java, exchangeRateWindow)
        )
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
}