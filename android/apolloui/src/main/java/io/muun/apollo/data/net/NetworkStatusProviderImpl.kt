package io.muun.apollo.data.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import io.muun.apollo.data.os.OS
import rx.Emitter
import rx.Observable
import javax.inject.Inject

internal class NetworkStatusProviderImpl @Inject constructor(
    applicationContext: Context,
) : NetworkStatusProvider {

    private val connectivityManager =
        applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override fun watchConnected(): Observable<Boolean> {
        // Like WorkManager, each change reads the active network again, since the callback's
        // network is unreliable when a VPN app is running.
        return watchNetworkChanges()
            .startWith(Unit)
            .map { isConnected() }
            .distinctUntilChanged()
    }

    /**
     * Emit on every change of the default network.
     * Before API 24 there is no default network callback, so it emits on every change of any
     * network with internet instead.
     */
    private fun watchNetworkChanges(): Observable<Unit> {
        return Observable.create(
            { emitter ->
                val callback = object : ConnectivityManager.NetworkCallback() {
                    // Before API 26, a network that connects may only call this.
                    override fun onAvailable(network: Network) {
                        emitter.onNext(Unit)
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        capabilities: NetworkCapabilities,
                    ) {
                        emitter.onNext(Unit)
                    }

                    override fun onLost(network: Network) {
                        emitter.onNext(Unit)
                    }
                }

                if (OS.supportsDefaultNetworkCallback()) {
                    connectivityManager.registerDefaultNetworkCallback(callback)
                } else {
                    val request = NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build()
                    connectivityManager.registerNetworkCallback(request, callback)
                }

                emitter.setCancellation {
                    connectivityManager.unregisterNetworkCallback(callback)
                }
            },
            Emitter.BackpressureMode.LATEST
        )
    }

    override fun isConnected(): Boolean {
        if (!OS.supportsActiveNetwork()) {
            return connectivityManager.activeNetworkInfo?.isConnected == true
        }

        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
