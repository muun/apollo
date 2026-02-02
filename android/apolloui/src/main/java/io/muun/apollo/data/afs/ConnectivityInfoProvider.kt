package io.muun.apollo.data.afs

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.os.Build
import androidx.annotation.RequiresApi
import io.muun.apollo.data.os.OS
import kotlinx.serialization.Serializable
import java.net.Inet6Address
import java.net.InetAddress
import java.util.Locale

class ConnectivityInfoProvider(context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    @Serializable
    data class NetworkLink(
        val interfaceName: String?,
        val routesSize: Int?,
        val hasGatewayRoute: Int,
        val linkHttpProxyHostType: Int,
    )

    private val KNOWN_LOOPBACK_HOSTS = setOf(
        "localhost",
        "localhost.localdomain",
        "loopback",
        "127.0.0.1",
        "::1"
    )

    private val KNOWN_INTERNAL_HOSTS = setOf(
        "local",
        "proxy.local",
        "proxy"
    )

    enum class AddressesType(val value: Int) {
        LOCALHOST(4),
        OTHER(3),
        INTERNAL(2),
        EMPTY(1),
        UNKNOWN(-1)
    }

    val vpnState: Int
        get() {
            if (OS.supportsActiveNetwork()) {
                return getVpnStateForNewerApi()

            } else {
                return getVpnStateForOldApi()
            }
        }

    val proxyHttpType: Int
        get() {
            return classifyHost(System.getProperty("http.proxyHost"))
        }

    val proxyHttpsType: Int
        get() {
            return classifyHost(System.getProperty("https.proxyHost"))
        }

    val proxySocksType: Int
        get() {
            return classifyHost(System.getProperty("socks.proxyHost"))
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
            val interfaceName = linkProperties?.interfaceName
            val linkHttpProxyHostType = classifyHost(linkProperties?.httpProxy?.host)
            val hasGatewayRoute = getHasGatewayRoute(linkProperties)
            return NetworkLink(
                interfaceName,
                routesSize,
                hasGatewayRoute,
                linkHttpProxyHostType
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

    // InetAddress.getByName does not detect IPv6 Unique Local Addresses (ULA).
    // ULA range: fc00::/7 → binary prefix 1111110x (0xFC = 11111100, 0xFD = 11111101)
    private fun isIPv6UniqueLocal(iAddress: InetAddress): Boolean {
        val bytes = iAddress.address
        return (iAddress is Inet6Address) && (bytes[0].toInt() and 0xFE) == 0xFC
    }

    /**
     * Classifies the given host as localhost, internal, external or unknown.
     *
     * Important:
     * - This method may trigger DNS resolution internally via `InetAddress.getByName()`,
     *   depending on the input. DNS lookups can be blocking and may require network access.
     * - Because of this, this method must not be called from the main thread.
     *
     * @return an integer value representing the host classification.
     */
    // Non private for @Test
    fun classifyHost(hostValue: String?): Int {
        if (hostValue.isNullOrBlank()) {
            return AddressesType.EMPTY.value
        }

        val cleanHost = hostValue.trim().lowercase(Locale.ROOT)

        if (KNOWN_LOOPBACK_HOSTS.contains(cleanHost)) {
            return AddressesType.LOCALHOST.value
        }
        if (KNOWN_INTERNAL_HOSTS.contains(cleanHost) || cleanHost.endsWith(".local")) {
            return AddressesType.INTERNAL.value
        }

        val address = try {
            InetAddress.getByName(cleanHost)
        } catch (_: Exception) {
            return AddressesType.UNKNOWN.value
        }

        return when {
            address.isLoopbackAddress -> AddressesType.LOCALHOST.value
            address.isSiteLocalAddress -> AddressesType.INTERNAL.value
            address.isLinkLocalAddress -> AddressesType.INTERNAL.value
            isIPv6UniqueLocal(address) -> AddressesType.INTERNAL.value
            else -> AddressesType.OTHER.value
        }
    }
}