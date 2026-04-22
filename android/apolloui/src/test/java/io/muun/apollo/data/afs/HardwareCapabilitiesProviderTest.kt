package io.muun.apollo.data.afs

import android.app.ActivityManager
import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

class HardwareCapabilitiesProviderTest {
    private lateinit var provider: HardwareCapabilitiesProvider

    @Before
    fun setup() {
        val context = mockk<Context>()
        val activityManager = mockk<ActivityManager>(relaxed = true)

        every { context.getSystemService(Context.ACTIVITY_SERVICE) } returns activityManager

        provider = spyk(HardwareCapabilitiesProvider(context))
    }

    @Test
    fun initOffsetWithInitCycleUnknown() {
        every { provider.getBootCycles() } returns Constants.INT_UNKNOWN
        assertEquals(Constants.INT_UNKNOWN, provider.bootOffset)
    }

    @Test
    fun initOffsetWithBootCountUnknown() {
        every { provider.bootCount() } returns Constants.INT_UNKNOWN
        assertEquals(Constants.INT_UNKNOWN, provider.bootOffset)
    }

    @Test
    fun initOffsetWithNoOffset() {
        val number = (1..100).random()

        every { provider.bootCount() } returns number
        every { provider.getBootCycles() } returns number

        assertEquals(0, provider.bootOffset)
    }

    @Test
    fun initOffsetWithOffset() {
        val number = (1..100).random()
        val otherNumber = number + (1..100).random()

        every { provider.bootCount() } returns number
        every { provider.getBootCycles() } returns otherNumber

        assertEquals(provider.bucketWithLowRangeDetail(abs(number - otherNumber)), provider.bootOffset)
    }
}