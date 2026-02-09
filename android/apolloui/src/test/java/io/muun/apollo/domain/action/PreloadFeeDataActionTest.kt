package io.muun.apollo.domain.action

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.muun.apollo.BaseTest
import io.muun.apollo.TestUtils
import io.muun.apollo.domain.action.realtime.PreloadFeeDataAction
import io.muun.apollo.domain.action.realtime.SyncRealTimeFees
import io.muun.apollo.domain.model.feebump.FeeBumpRefreshPolicy
import io.muun.apollo.domain.libwallet.FeeBumpFunctionsProvider
import org.junit.Before
import org.junit.Test
import rx.Observable

class PreloadFeeDataActionTest: BaseTest() {

    private val feeBumpFunctionsProvider = mockk<FeeBumpFunctionsProvider>(relaxed = true)
    private val syncRealTimeFees = mockk<SyncRealTimeFees>(relaxed = true)

    private lateinit var preloadFeeData: PreloadFeeDataAction

    @Before
    fun setUp() {
        preloadFeeData = PreloadFeeDataAction(syncRealTimeFees, feeBumpFunctionsProvider)
    }

    @Test
    fun testRunTwiceShouldRunOneTimeBecauseOfThrottling() {
        every { syncRealTimeFees.sync(any()) } returns Observable.just(null)
        every { syncRealTimeFees.shouldUpdateData() } returnsMany listOf(true, false)

        TestUtils.fetchItemFromObservable(preloadFeeData.action(FeeBumpRefreshPolicy.PERIODIC))

        TestUtils.fetchItemFromObservable(preloadFeeData.action(FeeBumpRefreshPolicy.PERIODIC))

        verify(exactly = 2) { syncRealTimeFees.shouldUpdateData() }
        verify(exactly = 1) { syncRealTimeFees.sync(any()) }

        // TODO we should test throttling logic (e.g multiple calls in after a threshold should
        //  run action multiple times)
    }

    @Test
    fun testForceRunTwiceShouldCallServicesTwoTimes() {
        every { syncRealTimeFees.sync(any()) } returns Observable.just(null)

        TestUtils.fetchItemFromObservable(preloadFeeData.runForced(FeeBumpRefreshPolicy.NTS_CHANGED))
        TestUtils.fetchItemFromObservable(preloadFeeData.runForced(FeeBumpRefreshPolicy.NTS_CHANGED))

        verify(exactly = 2) { syncRealTimeFees.sync(any()) }
    }
}