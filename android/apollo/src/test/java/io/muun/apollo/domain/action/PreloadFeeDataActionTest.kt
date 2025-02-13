package io.muun.apollo.domain.action

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.muun.apollo.BaseTest
import io.muun.apollo.TestUtils
import io.muun.apollo.data.external.Gen
import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.data.preferences.FeeWindowRepository
import io.muun.apollo.data.preferences.MinFeeRateRepository
import io.muun.apollo.data.preferences.TransactionSizeRepository
import io.muun.apollo.domain.action.realtime.PreloadFeeDataAction
import io.muun.apollo.domain.libwallet.FeeBumpRefreshPolicy
import io.muun.apollo.domain.libwallet.LibwalletService
import io.muun.apollo.domain.model.MuunFeature
import io.muun.apollo.domain.model.RealTimeFees
import io.muun.apollo.domain.selector.FeatureSelector
import io.muun.common.model.SizeForAmount
import io.muun.common.model.UtxoStatus
import org.junit.Before
import org.junit.Test
import org.threeten.bp.ZonedDateTime
import rx.Observable

class PreloadFeeDataActionTest: BaseTest() {

    private val feeWindowRepository = mockk<FeeWindowRepository>(relaxed = true)
    private val minFeeRateRepository = mockk<MinFeeRateRepository>(relaxed = true)
    private val transactionSizeRepository = mockk<TransactionSizeRepository>(relaxed = true)
    private val houstonClient = mockk<HoustonClient>()
    private val featureSelector = mockk<FeatureSelector>(relaxed = true)
    private val libwalletService = mockk<LibwalletService>(relaxed = true)

    private lateinit var preloadFeeData: PreloadFeeDataAction

    val feeBumpFunctions = Gen.feeBumpFunctions()
    val feeWindow = Gen.feeWindow()
    val minFeeRateInWeightUnits = 0.25
    val minMempoolFeeRateInSatPerVbyte = minFeeRateInWeightUnits * 4
    val minFeeRateIncrementToReplaceByFeeInSatPerVbyte = 2.5

    val realTimeFees = RealTimeFees(
        feeBumpFunctions,
        feeWindow,
        minMempoolFeeRateInSatPerVbyte,
        minFeeRateIncrementToReplaceByFeeInSatPerVbyte,
        ZonedDateTime.now()
    )

    private val sizeProgressionWithUnconfirmedUtxos = SizeForAmount(
        1000L,
        240,
        "default:0",
        UtxoStatus.UNCONFIRMED,
        240,
        "m/schema:1'/recovery:1'",
        1
    )

    @Before
    fun setUp() {
        preloadFeeData = PreloadFeeDataAction(
            houstonClient,
            feeWindowRepository,
            minFeeRateRepository,
            transactionSizeRepository,
            featureSelector,
            libwalletService
        )
    }

    @Test
    fun testRunTwiceShouldRunOneTimeBecauseOfThrottling() {
        every { houstonClient.fetchRealTimeFees(any(), any()) }.returns(Observable.just(realTimeFees))
        every { transactionSizeRepository.nextTransactionSize }
            .returns(Gen.nextTransactionSize(sizeProgressionWithUnconfirmedUtxos))
        every { featureSelector.get(MuunFeature.EFFECTIVE_FEES_CALCULATION) }.returns(true)

        TestUtils.fetchItemFromObservable(preloadFeeData.action(FeeBumpRefreshPolicy.PERIODIC))

        TestUtils.fetchItemFromObservable(preloadFeeData.action(FeeBumpRefreshPolicy.PERIODIC))

        verify(exactly = 1) { feeWindowRepository.store(feeWindow) }
        verify(exactly = 1) { minFeeRateRepository.store(minFeeRateInWeightUnits) }
        verify(exactly = 1) { libwalletService.persistFeeBumpFunctions(
            feeBumpFunctions,
            FeeBumpRefreshPolicy.PERIODIC
        ) }

        // TODO we should test throttling logic (e.g multiple calls in after a threshold should
        //  run action multiple times)
    }

    @Test
    fun testForceRunTwiceShouldCallServicesTwoTimes() {
        every { houstonClient.fetchRealTimeFees(any(), any()) }.returns(Observable.just(realTimeFees))
        every { transactionSizeRepository.nextTransactionSize }
            .returns(Gen.nextTransactionSize(sizeProgressionWithUnconfirmedUtxos))
        every { featureSelector.get(MuunFeature.EFFECTIVE_FEES_CALCULATION) }.returns(true)

        TestUtils.fetchItemFromObservable(preloadFeeData.runForced(FeeBumpRefreshPolicy.NTS_CHANGED))
        TestUtils.fetchItemFromObservable(preloadFeeData.runForced(FeeBumpRefreshPolicy.NTS_CHANGED))

        verify(exactly = 2) { feeWindowRepository.store(feeWindow) }
        verify(exactly = 2) { minFeeRateRepository.store(minFeeRateInWeightUnits) }
        verify(exactly = 2) { libwalletService.persistFeeBumpFunctions(
            feeBumpFunctions,
            FeeBumpRefreshPolicy.NTS_CHANGED
        ) }
    }
}