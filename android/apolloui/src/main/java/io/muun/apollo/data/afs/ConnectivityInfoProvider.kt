package io.muun.apollo.data.afs

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.os.Build
import androidx.annotation.RequiresApi
import io.muun.apollo.data.os.OS
import kotlinx.serialization.Serializable

class ConnectivityInfoProvider(context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    @Serializable
    data class NetworkLink(
        val interfaceName: String?,
        val routesSize: Int?,
        val routesInterfaces: Set<String>?,
        val hasGatewayRoute: Int,
        val dnsAddresses: Set<String>?,
        val linkHttpProxyHost: String?,
    )

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

    val proxyHttp: String
        get() {
            return System.getProperty("http.proxyHost") ?: Constants.EMPTY
        }

    val proxyHttps: String
        get() {
            return System.getProperty("https.proxyHost") ?: Constants.EMPTY
        }

    val proxySocks: String
        get() {
            return System.getProperty("socks.proxyHost") ?: Constants.EMPTY
        }


    /**
     * Retrieves the current network transport type of the device, available only for APIs >= 23.
     * We continue to use the NetworkInfo class for older versions,
     * as it is the only way to collect this data and is not deprecated in those API levels.
     * The responses remain consistent with the previous implementation, ensuring uniform data
     * handling across all Android versions
     */
    val activeNetworkTransport: String
        get() {
            if (!OS.supportsActiveNetwork()) {
                return Constants.UNKNOWN
            }

            val activeNetwork = connectivityManager.activeNetwork ?: return Constants.UNKNOWN
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                ?: return Constants.UNKNOWN

            return when {
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    -> "WIFI"

                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                    -> "MOBILE"

                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)
                    -> "BLUETOOTH"

                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                    -> "ETHERNET"

                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                    -> "VPN"

                else -> Constants.UNKNOWN
            }
        }

    val networkLink: NetworkLink?
        get() {
            if (!OS.supportsActiveNetwork()) {
                return null
            }

            val activeNetwork = connectivityManager.activeNetwork
            val linkProperties = connectivityManager.getLinkProperties(activeNetwork)

            val routesSize = linkProperties?.routes?.size
            val routesInterfaces = linkProperties?.routes?.mapNotNull { it.`interface` }?.toSet()
            val interfaceName = linkProperties?.interfaceName
            val dnsAddresses =
                linkProperties?.dnsServers?.map { it.hostAddress ?: Constants.EMPTY }?.toSet()
            val linkHttpProxyHost = linkProperties?.httpProxy?.host ?: Constants.EMPTY
            val hasGatewayRoute = getHasGatewayRoute(linkProperties)
            return NetworkLink(
                interfaceName,
                routesSize,
                routesInterfaces,
                hasGatewayRoute,
                dnsAddresses,
                linkHttpProxyHost
            )
        }

    private fun getHasGatewayRoute(linkProperties: LinkProperties?): Int {
        if (!OS.supportsRouteHasGateway()) {
            return Constants.INT_UNKNOWN
        }

        return linkProperties?.routes
            ?.any { route -> route.hasGateway() }
            ?.let { if (it) 1 else 0 }
            ?: 0
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