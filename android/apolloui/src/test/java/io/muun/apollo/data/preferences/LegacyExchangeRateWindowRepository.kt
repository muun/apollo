package io.muun.apollo.data.preferences

import android.content.Context
import io.muun.apollo.data.preferences.rx.Preference
import io.muun.apollo.data.serialization.SerializationUtils
import io.muun.apollo.domain.model.ExchangeRateWindow
import io.muun.common.Optional
import rx.Observable

/**
 * Test-only verbatim copy of ExchangeRateWindowRepository as of master (e4f75ed0176), before the
 * storage moved to libwallet. It is the reference implementation the parity test compares the
 * libwallet-backed repository against.
 */
class LegacyExchangeRateWindowRepository(
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

    val isSet: Boolean
        get() = windowPreference.isSet

    fun fetch(): Observable<ExchangeRateWindow> {
        return windowPreference.asObservable()
            .map { str: String? ->
                SerializationUtils.deserializeJson(
                    ExchangeRateWindow::class.java, str
                )
            }
    }

    fun fetchOne(): ExchangeRateWindow {
        return fetch().toBlocking().first()
    }

    fun storeLatest(exchangeRateWindow: ExchangeRateWindow) {
        windowPreference.set(
            SerializationUtils.serializeJson(ExchangeRateWindow::class.java, exchangeRateWindow)
        )
    }

    fun storeAndFix(exchangeRateWindow: ExchangeRateWindow) {
        fixedWindowPreference.set(
            SerializationUtils.serializeJson(ExchangeRateWindow::class.java, exchangeRateWindow)
        )
    }

    val fixedWindow: Optional<ExchangeRateWindow>
        get() = if (fixedWindowPreference.isSet) {
            val json = fixedWindowPreference.get()
            Optional.of(SerializationUtils.deserializeJson(ExchangeRateWindow::class.java, json))
        } else {
            Optional.empty()
        }
}
