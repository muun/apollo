package io.muun.apollo.domain.sync

import android.os.Handler
import android.os.Looper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.muun.apollo.BaseTest
import io.muun.apollo.data.external.Gen
import io.muun.apollo.data.preferences.TransactionSizeRepository
import io.muun.apollo.domain.action.NotificationActions
import io.muun.apollo.domain.action.NotificationProcessingState
import io.muun.apollo.domain.action.realtime.PreloadFeeDataAction
import io.muun.apollo.domain.model.MuunFeature
import io.muun.apollo.domain.selector.FeatureSelector
import io.muun.common.model.SizeForAmount
import io.muun.common.model.UtxoStatus
import org.junit.After
import org.junit.Before
import org.junit.Test
import rx.subjects.PublishSubject

class FeeDataSyncerTest: BaseTest() {
    private val preloadFeeData = mockk<PreloadFeeDataAction>(relaxed = true)
    private val transactionSizeRepository = mockk<TransactionSizeRepository>(relaxed = true)
    private val notificationActions = mockk<NotificationActions>(relaxed = true)
    private val featureSelector = mockk<FeatureSelector>(relaxed = true)
    private val processingSubject: PublishSubject<NotificationProcessingState> = PublishSubject.create()
    private val processingObservable = processingSubject.asObservable()

    private lateinit var feeDataSyncer: FeeDataSyncer

    private val initialSizeProgressionWithUnconfirmedUtxos = SizeForAmount(
        1000L,
        240,
        "a:0",
        UtxoStatus.UNCONFIRMED,
        240,
        "m/schema:1'/recovery:1'",
        1
    )

    private val initialSizeProgressionWithConfirmedUtxos = SizeForAmount(
        1000L,
        240,
        "a:0",
        UtxoStatus.CONFIRMED,
        240,
        "m/schema:1'/recovery:1'",
        1
    )

    private val finalSizeProgressionWithUnconfirmedUtxos = SizeForAmount(
        1000L,
        240,
        "b:0",
        UtxoStatus.UNCONFIRMED,
        240,
        "m/schema:1'/recovery:1'",
        1
    )

    @Before
    fun setUp() {
        every { featureSelector.get(MuunFeature.EFFECTIVE_FEES_CALCULATION) }.returns(true)
        every { notificationActions.getNotificationProcessingState() }.returns(processingObservable)

        mockkStatic(Looper::class)
        every { Looper.getMainLooper() } returns mockk()
        mockkConstructor(Handler::class)
        every { anyConstructed<Handler>().removeCallbacksAndMessages(any()) } returns Unit
        every { anyConstructed<Handler>().postDelayed(any(), any()) } returns true

        feeDataSyncer = FeeDataSyncer(
            preloadFeeData = preloadFeeData,
            nextTransactionSizeRepository = transactionSizeRepository,
            notificationActions = notificationActions,
            featureSelector = featureSelector
        )
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun testWithNTSEmptyShouldNotCallForceRun() {
        every { transactionSizeRepository.nextTransactionSize }
            .returns(null)

        feeDataSyncer.enterForeground()
        processingSubject.onNext(NotificationProcessingState.STARTED)
        verify(exactly = 0) { preloadFeeData.runForced() }

        processingSubject.onNext(NotificationProcessingState.COMPLETED)
        Thread.sleep(200)
        verify(exactly = 0) { preloadFeeData.runForced() }
    }

    @Test
    fun testWithNTSNotEmptyAndNoChangesShouldNotCallForceRun() {
        every { transactionSizeRepository.nextTransactionSize }
            .returns(Gen.nextTransactionSize(initialSizeProgressionWithUnconfirmedUtxos))

        feeDataSyncer.enterForeground()
        processingSubject.onNext(NotificationProcessingState.STARTED)
        verify(exactly = 0) { preloadFeeData.runForced() }

        processingSubject.onNext(NotificationProcessingState.COMPLETED)
        Thread.sleep(200)
        verify(exactly = 0) { preloadFeeData.runForced() }
    }

    @Test
    fun testWithNTSChangesAndUnconfirmedStatusShouldCallForceRun() {
        every { transactionSizeRepository.nextTransactionSize }
            .returns(Gen.nextTransactionSize(initialSizeProgressionWithUnconfirmedUtxos))

        feeDataSyncer.enterForeground()
        processingSubject.onNext(NotificationProcessingState.STARTED)
        verify(exactly = 0) { preloadFeeData.runForced() }

        every { transactionSizeRepository.nextTransactionSize }
            .returns(Gen.nextTransactionSize(finalSizeProgressionWithUnconfirmedUtxos))
        processingSubject.onNext(NotificationProcessingState.COMPLETED)
        Thread.sleep(200)
        verify(exactly = 1) { preloadFeeData.runForced() }
    }

    @Test
    fun testWithNTSChangesAndConfirmedStatusShouldNotCallForceRun() {
        every { transactionSizeRepository.nextTransactionSize }
            .returns(Gen.nextTransactionSize(initialSizeProgressionWithUnconfirmedUtxos))

        feeDataSyncer.enterForeground()
        processingSubject.onNext(NotificationProcessingState.STARTED)
        verify(exactly = 0) { preloadFeeData.runForced() }

        every { transactionSizeRepository.nextTransactionSize }
            .returns(Gen.nextTransactionSize(initialSizeProgressionWithConfirmedUtxos))
        processingSubject.onNext(NotificationProcessingState.COMPLETED)
        Thread.sleep(200)
        verify(exactly = 0) { preloadFeeData.runForced() }
    }
}