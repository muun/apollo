package io.muun.apollo.domain.action

import io.muun.apollo.BaseTest
import io.muun.apollo.TestUtils
import io.muun.apollo.data.external.Gen
import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.data.preferences.BlockchainHeightRepository
import io.muun.apollo.data.preferences.ExchangeRateWindowRepository
import io.muun.apollo.data.preferences.FeeWindowRepository
import io.muun.apollo.data.preferences.ForwardingPoliciesRepository
import io.muun.apollo.data.preferences.MinFeeRateRepository
import io.muun.apollo.domain.action.realtime.FetchRealTimeDataAction
import io.muun.apollo.domain.model.RealTimeData
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import rx.Observable


class FetchRealTimeDataActionTest : BaseTest() {

    @Mock
    private lateinit var exchangeRateWindowRepository: ExchangeRateWindowRepository

    @Mock
    private lateinit var feeWindowRepository: FeeWindowRepository

    @Mock
    private lateinit var blockchainHeightRepository: BlockchainHeightRepository

    @Mock
    private lateinit var forwardingPoliciesRepository: ForwardingPoliciesRepository

    @Mock
    private lateinit var minFeeRateRepository: MinFeeRateRepository

    @Mock
    private lateinit var houstonClient: HoustonClient

    private lateinit var fetchRealTimeDataAction: FetchRealTimeDataAction

    @Before
    fun setUp() {
        fetchRealTimeDataAction = FetchRealTimeDataAction(
                houstonClient,
                feeWindowRepository,
                exchangeRateWindowRepository,
                blockchainHeightRepository,
                forwardingPoliciesRepository,
                minFeeRateRepository
        )
    }

    @Test
    fun syncRealTimeData() {

        val feeWindow = Gen.feeWindow()
        val exchangeRateWindow = Gen.exchangeRateWindow()
        val blockchainHeight = 590000
        val forwardingPolicies = listOf(Gen.forwardingPolicy())
        val minFeeRateInWeightUnits = 0.25

        val realTimeData = RealTimeData(
                feeWindow,
                exchangeRateWindow,
                blockchainHeight,
                forwardingPolicies,
                minFeeRateInWeightUnits
        )

        Mockito.doReturn(Observable.just(realTimeData))
            .`when`(houstonClient).fetchRealTimeData()

        TestUtils.fetchItemFromObservable(fetchRealTimeDataAction.action())

        Mockito.verify(feeWindowRepository).store(feeWindow)
        Mockito.verify(exchangeRateWindowRepository).store(exchangeRateWindow)
        Mockito.verify(blockchainHeightRepository).store(blockchainHeight)
        Mockito.verify(forwardingPoliciesRepository).store(forwardingPolicies);
    }
}