package io.muun.apollo.domain.libwallet

import io.muun.apollo.BaseTest
import io.muun.apollo.domain.model.ExchangeRateWindow
import org.junit.Assert.assertEquals
import org.junit.Test
import org.threeten.bp.ZoneOffset
import org.threeten.bp.ZonedDateTime
import rpc.WalletServiceOuterClass.ExchangeRateWindow as RpcExchangeRateWindow

class ExchangeRateWindowRpcConversionTest : BaseTest() {

    companion object {
        private const val WINDOW_ID = 7L
        private const val FETCH_DATE_IN_MILLIS = 1_792_670_400_123L

        private val FETCH_DATE =
            ZonedDateTime.of(2026, 10, 22, 12, 0, 0, 123_000_000, ZoneOffset.UTC)
        private val RATES = mapOf("USD" to 65_000.5, "ARS" to 95_000_000.25)
    }

    @Test
    fun `toRpc should map every field`() {
        // GIVEN
        val window = ExchangeRateWindow(WINDOW_ID, FETCH_DATE, RATES)

        // WHEN
        val rpcWindow = window.toRpc()

        // THEN
        assertEquals(WINDOW_ID, rpcWindow.windowId)
        assertEquals(FETCH_DATE_IN_MILLIS, rpcWindow.fetchDateInMillis)
        assertEquals(RATES, rpcWindow.ratesMap)
    }

    @Test
    fun `toExchangeRateWindow should map every field`() {
        // GIVEN
        val rpcWindow = RpcExchangeRateWindow.newBuilder()
            .setWindowId(WINDOW_ID)
            .setFetchDateInMillis(FETCH_DATE_IN_MILLIS)
            .putAllRates(RATES)
            .build()

        // WHEN
        val window = rpcWindow.toExchangeRateWindow()

        // THEN
        assertEquals(WINDOW_ID, window.windowHid)
        assertEquals(FETCH_DATE, window.fetchDate)
        assertEquals(RATES, window.rates)
    }
}
