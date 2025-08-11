package io.muun.apollo.domain.action

import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.muun.apollo.BaseTest
import io.muun.apollo.TestUtils
import io.muun.apollo.data.external.Gen
import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.data.preferences.BlockchainHeightRepository
import io.muun.apollo.data.preferences.ExchangeRateWindowRepository
import io.muun.apollo.data.preferences.FeaturesRepository
import io.muun.apollo.data.preferences.FeeWindowRepository
import io.muun.apollo.data.preferences.ForwardingPoliciesRepository
import io.muun.apollo.data.preferences.MinFeeRateRepository
import io.muun.apollo.domain.action.realtime.FetchRealTimeDataAction
import io.muun.apollo.domain.model.MuunFeature
import io.muun.apollo.domain.model.RealTimeData
import org.junit.Before
import org.junit.Test
import rx.Observable


class FetchRealTimeDataActionTest : BaseTest() {

    private val exchangeRateWindowRepository = mockk<ExchangeRateWindowRepository>(relaxed = true)

    private val feeWindowRepository = mockk<FeeWindowRepository>(relaxed = true)

    private val blockchainHeightRepository = mockk<BlockchainHeightRepository>(relaxed = true)

    private val forwardingPoliciesRepository = mockk<ForwardingPoliciesRepository>(relaxed = true)

    private val minFeeRateRepository = mockk<MinFeeRateRepository>(relaxed = true)

    private val featuresRepository = mockk<FeaturesRepository>(relaxed = true)

    private val houstonClient = mockk<HoustonClient>()

    private lateinit var fetchRealTimeDataAction: FetchRealTimeDataAction

    @Before
    fun setUp() {
        fetchRealTimeDataAction = FetchRealTimeDataAction(
            houstonClient,
            feeWindowRepository,
            exchangeRateWindowRepository,
            blockchainHeightRepository,
            forwardingPoliciesRepository,
            minFeeRateRepository,
            featuresRepository,
        )
    }

    @Test
    fun syncRealTimeData() {

        val feeWindow = Gen.feeWindow()
        val exchangeRateWindow = Gen.exchangeRateWindow()
        val blockchainHeight = 590000
        val forwardingPolicies = listOf(Gen.forwardingPolicy())
        val minFeeRateInWeightUnits = 0.25
        val listOfFeatures = listOf(MuunFeature.TAPROOT)

        val realTimeData = RealTimeData(
            feeWindow,
            exchangeRateWindow,
            blockchainHeight,
            forwardingPolicies,
            minFeeRateInWeightUnits,
            listOfFeatures
        )

        every { houstonClient.fetchRealTimeData() }.returns(Observable.just(realTimeData))

        TestUtils.fetchItemFromObservable(fetchRealTimeDataAction.action())

        verify { feeWindowRepository.store(feeWindow) }
        verify { exchangeRateWindowRepository.storeLatest(exchangeRateWindow) }
        verify { blockchainHeightRepository.store(blockchainHeight) }
        verify { forwardingPoliciesRepository.store(forwardingPolicies) }
        verify { minFeeRateRepository.store(minFeeRateInWeightUnits) }
        verify { featuresRepository.store(listOfFeatures) }

        confirmVerified(blockchainHeightRepository)
        confirmVerified(forwardingPoliciesRepository)
        confirmVerified(minFeeRateRepository)
        confirmVerified(featuresRepository)

        // TODO we should test shouldSync() logic (e.g multiple syncs in short term don't fetch
        //  new data, but they do after threshold)
    }
}