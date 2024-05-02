package io.muun.apollo.data.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.annotation.RequiresApi
import io.muun.apollo.data.os.Constants
import io.muun.apollo.data.os.OS
import javax.inject.Inject

// TODO we should merge this and NetworkInfoProvider together
class ConnectivityInfoProvider @Inject constructor(context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val vpnState: Int
        get() {
            if (OS.supportsActiveNetwork()) {
                return getVpnStateForNewerApi()

            } else {

                if (OS.supportsNetworkCapabilities()) {
                    return getVpnStateForOldApi()

                } else {
                    return Constants.INT_UNKNOWN
                }
            }
        }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun getVpnStateForNewerApi(): Int {
        val activeNetwork = connectivityManager.activeNetwork
        if (activeNetwork != null) {
            val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
            if (caps != null) {
                return if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) 1 else 0
            }
        }

        // if no activeNetwork or no networkCapabilities then return unknown
        return Constants.INT_UNKNOWN
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun getVpnStateForOldApi(): Int {
        val allNetworks = connectivityManager.allNetworks
        val isVpnNetworkAvailable = allNetworks.any { network ->
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
            networkCapabilities != null && networkCapabilities.hasTransport(
                NetworkCapabilities.TRANSPORT_VPN
            )
        }

        return if (isVpnNetworkAvailable) 2 else 3
    }
}