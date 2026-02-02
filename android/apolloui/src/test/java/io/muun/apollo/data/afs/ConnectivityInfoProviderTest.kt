package io.muun.apollo.data.afs

import io.muun.apollo.data.afs.ConnectivityInfoProvider.AddressesType

import android.content.Context
import android.net.ConnectivityManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test


class ConnectivityInfoProviderTest {

    private lateinit var provider: ConnectivityInfoProvider

    @Before
    fun setup() {
        val context = mockk<Context>(relaxed = true)
        val connectivityManager = mockk<ConnectivityManager>(relaxed = true)

        every {
            context.getSystemService(Context.CONNECTIVITY_SERVICE)
        } returns connectivityManager

        provider = ConnectivityInfoProvider(context)
    }

    @Test
    fun emptyOrBlankReturnsEmpty() {
        assertEquals(AddressesType.EMPTY.value, provider.classifyHost(null))
        assertEquals(AddressesType.EMPTY.value, provider.classifyHost(""))
        assertEquals(AddressesType.EMPTY.value, provider.classifyHost("   "))
    }

    @Test
    fun localhostVariantsReturnLocalhost() {
        assertEquals(AddressesType.LOCALHOST.value, provider.classifyHost("localhost"))
        assertEquals(AddressesType.LOCALHOST.value, provider.classifyHost("LOCALHOST"))
        assertEquals(AddressesType.LOCALHOST.value, provider.classifyHost("127.0.0.1"))
        assertEquals(AddressesType.LOCALHOST.value, provider.classifyHost("::1"))
        assertEquals(AddressesType.LOCALHOST.value, provider.classifyHost("0:0:0:0:0:0:0:1"))
    }

    @Test
    fun internalHostReturnsInternal() {
        assertEquals(AddressesType.INTERNAL.value, provider.classifyHost("local"))
        assertEquals(AddressesType.INTERNAL.value, provider.classifyHost("muun.local"))
        assertEquals(AddressesType.INTERNAL.value, provider.classifyHost("192.168.1.10"))
        assertEquals(AddressesType.INTERNAL.value, provider.classifyHost("fc12:3456:789a:1::10"))
        assertEquals(AddressesType.INTERNAL.value, provider.classifyHost("fd00:1234:5678::1"))
        assertEquals(AddressesType.INTERNAL.value, provider.classifyHost("fd12:3456:789a::1"))
    }

    @Test
    fun invalidHostReturnsUnknown() {
        assertEquals(AddressesType.UNKNOWN.value, provider.classifyHost("this-is-not-a-host"))
        assertEquals(AddressesType.UNKNOWN.value, provider.classifyHost("255.234.0.260"))
        assertEquals(AddressesType.UNKNOWN.value, provider.classifyHost("2001:cb0::fc0::abc"))
    }

    @Test
    fun externalHostReturnsOther() {
        assertEquals(AddressesType.OTHER.value, provider.classifyHost("8.8.8.8"))
        assertEquals(AddressesType.OTHER.value, provider.classifyHost("www.google.com"))
        assertEquals(AddressesType.OTHER.value, provider.classifyHost("2001:4860:4860::8844"))
    }
}