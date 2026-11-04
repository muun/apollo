package io.muun.apollo.data.libwallet

import io.mockk.every
import io.mockk.mockk
import io.muun.apollo.BaseTest
import io.muun.apollo.domain.action.NotificationActions
import io.muun.apollo.domain.action.incoming_swap.RegisterInvoicesAction
import io.muun.apollo.domain.action.realtime.FetchRealTimeDataAction
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import rx.Observable

class PeriodicActionsBridgeTest : BaseTest() {

    private val notificationActions = mockk<NotificationActions>()
    private val fetchRealTimeData = mockk<FetchRealTimeDataAction>()
    private val registerInvoices = mockk<RegisterInvoicesAction>()

    private val bridge = PeriodicActionsBridge(
        { notificationActions },
        { fetchRealTimeData },
        { registerInvoices },
    )

    @Test
    fun `an action that succeeds returns`() {
        every { notificationActions.pullNotifications() } returns Observable.just(null)

        bridge.pullNotifications(1000)
    }

    @Test
    fun `an action that fails throws its error`() {
        val error = RuntimeException("houston is down")
        every { registerInvoices.action() } returns Observable.error(error)

        assertThatThrownBy { bridge.registerInvoices(1000) }.isSameAs(error)
    }

    @Test
    fun `an action that does not end in time throws`() {
        every { fetchRealTimeData.action() } returns Observable.never()

        val start = System.currentTimeMillis()
        assertThatThrownBy { bridge.syncRealTimeData(100) }
        val elapsed = System.currentTimeMillis() - start

        assertThat(elapsed).isBetween(100L, 1000L)
    }
}
