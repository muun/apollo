package io.muun.apollo.domain.action

import io.mockk.called
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import io.mockk.verifySequence
import io.muun.apollo.BaseTest
import io.muun.apollo.TestExecutor
import io.muun.apollo.data.external.AppStandbyBucketProvider
import io.muun.apollo.data.external.NotificationService
import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.data.net.ModelObjectsMapper
import io.muun.apollo.data.preferences.NotificationRepository
import io.muun.apollo.domain.NotificationProcessor
import io.muun.apollo.domain.action.base.AsyncActionStore
import io.muun.apollo.domain.action.incoming_swap.FulfillIncomingSwapAction
import io.muun.apollo.domain.action.operation.CreateOperationAction
import io.muun.apollo.domain.action.operation.OperationMetadataMapper
import io.muun.apollo.domain.action.operation.UpdateOperationAction
import io.muun.apollo.domain.action.realtime.FetchRealTimeDataAction
import io.muun.apollo.domain.model.NotificationReport
import io.muun.common.Optional
import io.muun.common.api.beam.notification.NotificationJson
import io.muun.common.api.messages.FulfillIncomingSwapMessage
import io.muun.common.api.messages.MessageOrigin
import io.muun.common.api.messages.MessageSpec
import io.muun.common.model.SessionStatus
import org.bitcoinj.params.TestNet3Params
import org.junit.Before
import org.junit.Test
import rx.Completable
import rx.Observable
import rx.functions.Func2
import rx.schedulers.Schedulers

class NotificationActionsTest : BaseTest() {

    private val signinActions = mockk<SigninActions>(relaxed = true)
    private val contactActions = mockk<ContactActions>(relaxed = true)
    private val userActions = mockk<UserActions>(relaxed = true)
    private val createOperationAction = mockk<CreateOperationAction>(relaxed = true)
    private val updateOperationAction = mockk<UpdateOperationAction>(relaxed = true)
    private val fetchRealTimeDataAction = mockk<FetchRealTimeDataAction>(relaxed = true)
    private val notificationRepository = mockk<NotificationRepository>(relaxed = true)
    private val asyncActionStore = mockk<AsyncActionStore>(relaxed = true)
    private val operationMapper = mockk<OperationMetadataMapper>(relaxed = true)
    private val fulfillIncomingSwap = mockk<FulfillIncomingSwapAction>(relaxed = true)
    private val notificationService = mockk<NotificationService>(relaxed = true)

    private val houstonClient = mockk<HoustonClient>(relaxed = true)

    private val executor = TestExecutor()
    private val mapper = ModelObjectsMapper(TestNet3Params.get())

    private lateinit var notificationActions: NotificationActions
    private lateinit var testMessageHandler: TestMessageHandler
    private lateinit var explodingMessageHandler: ExplodingMessageHandler

    private var savedLastProcessedId: Long = 0

    @Before
    fun setUp() {

        every {
            houstonClient.confirmNotificationsDeliveryUntil(any(), any(), any(), any())
        } returns
            Observable.just(null)

        val notificationProcessor = NotificationProcessor(
            updateOperationAction,
            createOperationAction,
            fetchRealTimeDataAction,
            contactActions,
            userActions,
            signinActions,
            mapper,
            operationMapper,
            fulfillIncomingSwap,
            houstonClient,
            notificationService
        )
        testMessageHandler = spyk(TestMessageHandler())
        notificationProcessor.addHandler(TEST_SPEC, testMessageHandler)
        explodingMessageHandler = spyk(ExplodingMessageHandler())
        notificationProcessor.addHandler(EXPLODING_SPEC, explodingMessageHandler)
        notificationProcessor.addHandler(FulfillIncomingSwapMessage.SPEC, explodingMessageHandler)

        // Provide realistic notificationRepository lastProcessedId, saving it to a variable:
        notificationActions = spyk(
            NotificationActions(
                notificationRepository,
                houstonClient,
                asyncActionStore,
                notificationProcessor,
                { AppStandbyBucketProvider.Bucket.ACTIVE },
                Schedulers.from(executor)
            )
        )
        savedLastProcessedId = 0

        every {
            notificationRepository setProperty "lastProcessedId" value any(Long::class)
        } answers {
            savedLastProcessedId = firstArg()
        }

        every { notificationRepository.lastProcessedId } answers {
            savedLastProcessedId
        }
//        every { notificationRepository getProperty "lastProcessedId" } propertyType Long::class answers {
//            savedLastProcessedId
//        }

        // Fake always being logged in:
        every { signinActions.sessionStatus } returns Optional.of(SessionStatus.LOGGED_IN)
    }

    @Test
    fun onNotificationReport_isAsync() {
        // This test ensures the actual processing of a notification does not occur during the
        // execution of onNotificationReport.

        // This is important: onNotificationReport will be invoked from a GCM handler that's
        // expected to finish within a short timespan.
        val notif: NotificationJson = TestNotification.withId(1L)
        executor.pause()
        notificationActions.onNotificationReport(reportOf(notif))

        verify { testMessageHandler wasNot called }

        executor.resume()
        executor.waitUntilFinished()

        verify(exactly = 1) { testMessageHandler.call(notif, 0L) }
    }

    @Test
    fun processReport_isSequential() {
        val notif1 = TestNotification.withId(1L)
        val notif2 = TestNotification.withId(2L)

        notificationActions.onNotificationReport(reportOf(notif1))
        notificationActions.onNotificationReport(reportOf(notif2))

        executor.waitUntilFinished()

        verifySequence {
            testMessageHandler.call(notif1, any())
            testMessageHandler.call(notif2, any())
        }
    }

    @Test
    fun processReport_correctDispatch() {
        val notification: NotificationJson = TestNotification.withId(1L)

        notificationActions.onNotificationReport(reportOf(notification))
        executor.waitUntilFinished()

        verify(exactly = 1) { testMessageHandler.call(notification, 0L) }
    }

    @Test
    fun processReport_detectsGapBefore() {
        val first: NotificationJson = TestNotification.withId(2L) // gap before!
        val second: NotificationJson = TestNotification.withId(3L)

        every {
            houstonClient.fetchNotificationReportAfter(any(Long::class))
        } returns
            Observable.just(reportOf(first, second))

        val report = NotificationReport(first.previousId, second.id, listOf(first))
        notificationActions.onNotificationReport(report)
        executor.waitUntilFinished()

        verify(exactly = 1) { houstonClient.fetchNotificationReportAfter(0L) }
    }

    @Test
    fun processReport_detectsGapAfter() {
        val first: NotificationJson = TestNotification.withId(1L)
        val second: NotificationJson = TestNotification.withId(2L)

        every {
            houstonClient.fetchNotificationReportAfter(any(Long::class))
        } returns
            Observable.just(reportOf(second))

        val report = NotificationReport(first.previousId, first.id + 1, listOf(first))
        notificationActions.onNotificationReport(report)
        executor.waitUntilFinished()

        verify(exactly = 1) { houstonClient.fetchNotificationReportAfter(1L) }
    }

    @Test
    fun processReport_detectNoGaps() {
        savedLastProcessedId = 1L
        val first: NotificationJson = TestNotification.withId(2L)
        val report = NotificationReport(first.previousId, first.id, listOf(first))

        notificationActions.onNotificationReport(report)
        executor.waitUntilFinished()


        verify(exactly = 0) { houstonClient.fetchNotificationReportAfter(savedLastProcessedId) }
    }

    @Test
    fun processNotificationList_skipErrors() {
        val list = listOf<NotificationJson>(
            TestNotification.withId(1L),
            TestNotification.explodingWithId(2L),
            TestNotification.withId(3L) // will be processed, even if previous fails
        )

        notificationActions.onNotificationReport(reportOf(list))
        executor.waitUntilFinished()


        verify(exactly = 1) { testMessageHandler.call(list[0], 0L) }
        verify(exactly = 1) { explodingMessageHandler.call(list[1], 0L) }
        verify(exactly = 1) { testMessageHandler.call(list[2], 0L) }
        verify(exactly = 1) {
            notificationRepository setProperty "lastProcessedId" value list[2].id
        }
    }

    @Test
    fun processNotificationList_fulfillIsNotSkippedOnError() {
        val list = listOf<NotificationJson>(
            TestNotification.withId(1L),
            TestNotification.fulfillWithId(2L),
            TestNotification.withId(3L) // will not be processed
        )

        notificationActions.onNotificationReport(reportOf(list))
        executor.waitUntilFinished()

        verify(exactly = 1) { testMessageHandler.call(list[0], 0L) }
        verify(exactly = 1) { explodingMessageHandler.call(list[1], 0L) }

        confirmVerified(testMessageHandler)

        // Only the first notification is processed
        verify(exactly = 1) {
            notificationRepository setProperty "lastProcessedId" value list[0].id
        }
    }

    @Test
    fun processNotificationList_confirmDelivery() {
        val list = listOf<NotificationJson>(
            TestNotification.withId(1L),
            TestNotification.withId(2L)
        )

        notificationActions.onNotificationReport(reportOf(list))
        executor.waitUntilFinished()

        verify {
            houstonClient.confirmNotificationsDeliveryUntil(
                list[1].id,
                any(),
                any(),
                any()
            )
        }
    }

    @Test
    fun processNotification_updatesLastProcessedId() {
        savedLastProcessedId = 1L
        val notification: NotificationJson = TestNotification.withId(2L)

        notificationActions.onNotificationReport(reportOf(notification))
        executor.waitUntilFinished()

        verify(exactly = 1) {
            notificationRepository setProperty "lastProcessedId" value notification.id
        }
    }

    @Test
    fun onNotificationReport_fetchPaginated() {

        // Verify that we keep on fetching pages until there's no more new notifications, as given
        // by the max id in the report.
        val first: NotificationJson = TestNotification.withId(1L)
        val second: NotificationJson = TestNotification.withId(2L)
        val third: NotificationJson = TestNotification.withId(3L)
        val fourth: NotificationJson = TestNotification.withId(4L)

        every {
            houstonClient.fetchNotificationReportAfter(1L)
        } returns
            Observable.just(reportWithMaxId(4, second))

        every {
            houstonClient.fetchNotificationReportAfter(2L)
        } returns
            Observable.just(reportWithMaxId(4, third))

        every {
            houstonClient.fetchNotificationReportAfter(3L)
        } returns
            Observable.just(reportWithMaxId(4, fourth))

        notificationActions.onNotificationReport(reportWithMaxId(4, first))
        executor.waitUntilFinished()

        verify(exactly = 1) { houstonClient.fetchNotificationReportAfter(1L) }
        verify(exactly = 1) { houstonClient.fetchNotificationReportAfter(2L) }
        verify(exactly = 1) { houstonClient.fetchNotificationReportAfter(3L) }
        verify {
            houstonClient.confirmNotificationsDeliveryUntil(
                4L,
                any(),
                any(),
                any()
            )
        }
        verify(exactly = 1) {
            notificationRepository setProperty "lastProcessedId" value fourth.id
        }
    }

    @Test
    fun onNotificationReport_emptyPreviewWithHigherMaxCausesFetch() {

        // If we get an empty preview, we should fetch unprocessed notifications from the backend.
        // This happens if our notifications are to big to fit in a FCM message.
        val first: NotificationJson = TestNotification.withId(1L)
        val second: NotificationJson = TestNotification.withId(2L)
        val third: NotificationJson = TestNotification.withId(3L)
        val fourth: NotificationJson = TestNotification.withId(4L)

        // We return single element pages to force several pages to be fetched

        every {
            houstonClient.fetchNotificationReportAfter(2L)
        } returns
            Observable.just(reportWithMaxId(4, third))

        every {
            houstonClient.fetchNotificationReportAfter(3L)
        } returns
            Observable.just(reportWithMaxId(4, fourth))

        // First process a normal report to seed
        notificationActions.onNotificationReport(reportOf(first, second))
        notificationActions.onNotificationReport(reportWithMaxId(4))
        executor.waitUntilFinished()

        verify(exactly = 1) { houstonClient.fetchNotificationReportAfter(2L) }
        verify(exactly = 1) { houstonClient.fetchNotificationReportAfter(3L) }
    }

    @Test
    fun onNotificationReport_emptyPreviewWithSameMaxDoesNothing() {

        // If we get an empty preview with the same max id, we should do nothing.
        val first: NotificationJson = TestNotification.withId(1L)
        val second: NotificationJson = TestNotification.withId(2L)

        // First process a normal report to seed
        notificationActions.onNotificationReport(reportOf(first, second))
        notificationActions.onNotificationReport(reportWithMaxId(2))
        executor.waitUntilFinished()

        verify(exactly = 0) { houstonClient.fetchNotificationReportAfter(any(Long::class)) }
    }

    @Test
    fun onNotificationReport_failureWithPagination() {
        // Verify that failure to process one notification won't stop us from fetching remaining
        // pages.
        val first: NotificationJson = TestNotification.withId(1L)
        val second: NotificationJson = TestNotification.explodingWithId(2L)
        val third: NotificationJson = TestNotification.withId(3L)

        every {
            houstonClient.fetchNotificationReportAfter(2L)
        } returns
            Observable.just(reportWithMaxId(3, third))

        notificationActions.onNotificationReport(reportWithMaxId(3, first, second))
        executor.waitUntilFinished()


        verify(exactly = 1) {
            notificationRepository setProperty "lastProcessedId" value first.id
        }
        verify(exactly = 1) {
            notificationRepository setProperty "lastProcessedId" value third.id
        }
    }

    @Test
    fun onNotificationReport_moreThanOnePageWithReport() {

        // Verify that if we get a report with a previousId that's several pages bigger than our
        // max processed id, we'll fetch all the pages in-between.
        val first: NotificationJson = TestNotification.withId(1L)
        val second: NotificationJson = TestNotification.withId(2L)
        val third: NotificationJson = TestNotification.withId(3L)

        every {
            houstonClient.fetchNotificationReportAfter(0L)
        } returns
            Observable.just(reportWithMaxId(3, first))
        every {
            houstonClient.fetchNotificationReportAfter(1L)
        } returns
            Observable.just(reportWithMaxId(3, second))
        every {
            houstonClient.fetchNotificationReportAfter(2L)
        } returns
            Observable.just(reportWithMaxId(3, third))

        notificationActions.onNotificationReport(reportWithMaxId(3, third))
        executor.waitUntilFinished()

        verify(exactly = 1) { houstonClient.fetchNotificationReportAfter(0L) }
        verify(exactly = 1) { houstonClient.fetchNotificationReportAfter(1L) }
        verify(exactly = 1) { houstonClient.fetchNotificationReportAfter(2L) }
        verify(exactly = 1) {
            notificationRepository setProperty "lastProcessedId" value first.id
        }
        verify(exactly = 1) {
            notificationRepository setProperty "lastProcessedId" value second.id
        }
        verify(exactly = 1) {
            notificationRepository setProperty "lastProcessedId" value third.id
        }
    }

    private class TestMessageHandler : Func2<NotificationJson, Long, Completable> {
        override fun call(notificationJson: NotificationJson, retries: Long): Completable {
            return Completable.complete()
        }
    }

    private class ExplodingMessageHandler : Func2<NotificationJson, Long, Completable> {
        override fun call(json: NotificationJson, retries: Long): Completable {
            return Completable.error(RuntimeException())
        }
    }

    private class TestNotification private constructor(id: Long, spec: MessageSpec) :
        NotificationJson(
            id,
            id - 1,
            "414e27a7-a59d-44be-a0f3-41440d1de669",
            "414e27a7-a59d-44be-a0f3-41440d1de669",
            spec.messageType,
            HashMap<String, String>(),
            null
        ) {

        companion object {
            fun withId(id: Long): TestNotification {
                return TestNotification(id, TEST_SPEC)
            }

            fun explodingWithId(id: Long): TestNotification {
                return TestNotification(id, EXPLODING_SPEC)
            }

            fun fulfillWithId(id: Long): TestNotification {
                return TestNotification(id, FulfillIncomingSwapMessage.SPEC)
            }
        }
    }

    private fun reportWithMaxId(maxId: Long, vararg notifs: NotificationJson): NotificationReport {
        return NotificationReport(
            if (notifs.isEmpty()) maxId else notifs[0].previousId,
            maxId,
            listOf(*notifs)
        )
    }

    private fun reportOf(vararg notifs: NotificationJson): NotificationReport {
        return reportOf(listOf(*notifs))
    }

    private fun reportOf(notifs: List<NotificationJson>): NotificationReport {
        return NotificationReport(
            notifs[0].previousId,
            notifs[notifs.size - 1].id,
            notifs
        )
    }

    companion object {
        val TEST_SPEC = MessageSpec(
            "test",
            SessionStatus.CREATED,
            MessageOrigin.ANY
        )
        val EXPLODING_SPEC = MessageSpec(
            "BOOM!",
            SessionStatus.CREATED,
            MessageOrigin.ANY
        )
    }
}