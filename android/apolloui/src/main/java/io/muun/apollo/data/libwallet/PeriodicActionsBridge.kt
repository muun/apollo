package io.muun.apollo.data.libwallet

import dagger.Lazy
import io.muun.apollo.domain.action.NotificationActions
import io.muun.apollo.domain.action.incoming_swap.RegisterInvoicesAction
import io.muun.apollo.domain.action.realtime.FetchRealTimeDataAction
import rx.Observable
import java.util.concurrent.TimeUnit
import javax.inject.Inject

// The actions are lazy because they depend on the libwallet client, which needs the Config
// this bridge is part of.
class PeriodicActionsBridge @Inject constructor(
    private val notificationActions: Lazy<NotificationActions>,
    private val fetchRealTimeData: Lazy<FetchRealTimeDataAction>,
    private val registerInvoices: Lazy<RegisterInvoicesAction>,
) : app_provided_data.PeriodicActionsBridge {

    override fun pullNotifications(timeoutMillis: Long) {
        notificationActions.get().pullNotifications().await(timeoutMillis)
    }

    override fun syncRealTimeData(timeoutMillis: Long) {
        fetchRealTimeData.get().action().await(timeoutMillis)
    }

    override fun registerInvoices(timeoutMillis: Long) {
        registerInvoices.get().action().await(timeoutMillis)
    }

    private fun Observable<Void>.await(timeoutMillis: Long) {
        timeout(timeoutMillis, TimeUnit.MILLISECONDS).toBlocking().lastOrDefault(null)
    }
}
