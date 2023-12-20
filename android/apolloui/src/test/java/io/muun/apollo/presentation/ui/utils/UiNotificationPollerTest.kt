package io.muun.apollo.presentation.ui.utils

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import io.muun.apollo.data.net.base.NetworkException
import io.muun.apollo.data.os.execution.ExecutionTransformerFactory
import io.muun.apollo.domain.action.NotificationPoller
import io.muun.apollo.domain.errors.ExpiredSessionError
import io.muun.apollo.presentation.BasePresentationTest
import io.muun.common.api.error.ErrorCode
import io.muun.common.exception.HttpException
import org.assertj.core.api.Assertions
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import rx.Observable
import rx.schedulers.Schedulers
import rx.schedulers.TestScheduler
import rx.subjects.BehaviorSubject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class UiNotificationPollerTest : BasePresentationTest() {

    private val pollingIntervalInSecs = 1

    private var notifications: BehaviorSubject<Void> = BehaviorSubject.create()

    private val executionTransformerFactorySpy = Mockito.spy(
        ExecutionTransformerFactory(
            Executors.newSingleThreadExecutor(),
            Schedulers.newThread()
        )
    )

    private lateinit var uiNotificationPoller: UiNotificationPoller

    private var fakeNotificationPoller = object : NotificationPoller {
        var called: Int = 0
        var success: Int = 0
        var error: Exception? = null

        fun startThrowing(error: Exception) {
            this.error = error
        }

        fun noMoreError() {
            this.error = null
        }

        override fun pullNotifications(): Observable<Void> = Observable.defer {

            called++

            if (error != null) {
                throw error!!
            }

            success++
            notifications = BehaviorSubject.create()
            notifications.onNext(null)
            notifications.onCompleted()
            return@defer notifications
        }
    }

    @Before
    fun setUp() {
        uiNotificationPoller = UiNotificationPoller(
            fakeNotificationPoller,
            executionTransformerFactorySpy
        )
        // Setting for consistency between local and CI
        uiNotificationPoller.setPollingIntervalInMillisForTesting(pollingIntervalInSecs * 1000)
    }

    @After
    fun cleanUp() {
        uiNotificationPoller.stop()
        Mockito.reset(executionTransformerFactorySpy)
    }

    @Test
    fun `pulls notifications at some interval`() {
        val testScheduler = TestScheduler()
        spyOnTransformerFactoryToUseTestScheduler(testScheduler)

        uiNotificationPoller.start()

        testScheduler.advanceTimeBy(pollingIntervalInSecs * 4L, TimeUnit.SECONDS)

        Assertions.assertThat(fakeNotificationPoller.called).isGreaterThan(2)
    }

    @Test
    fun `stops pulling notifications on stop`() {
        val testScheduler = TestScheduler()
        spyOnTransformerFactoryToUseTestScheduler(testScheduler)

        uiNotificationPoller.start()

        testScheduler.advanceTimeBy(pollingIntervalInSecs * 4L, TimeUnit.SECONDS)

        uiNotificationPoller.stop()

        val hits = fakeNotificationPoller.success
        Assertions.assertThat(hits).isGreaterThan(2)

        testScheduler.advanceTimeBy(pollingIntervalInSecs * 4L, TimeUnit.SECONDS)

        Assertions.assertThat(fakeNotificationPoller.success).isEqualTo(hits)
        Assertions.assertThat(fakeNotificationPoller.called).isEqualTo(hits)
    }

    @Test
    fun `stop polling on ExpiredSession error`() {
        val testScheduler = TestScheduler()
        spyOnTransformerFactoryToUseTestScheduler(testScheduler)

        uiNotificationPoller.start()

        testScheduler.advanceTimeBy(pollingIntervalInSecs * 4L, TimeUnit.SECONDS)

        Assertions.assertThat(fakeNotificationPoller.success).isGreaterThan(2)

        fakeNotificationPoller.startThrowing(ExpiredSessionError())

        testScheduler.advanceTimeBy(pollingIntervalInSecs * 4L, TimeUnit.SECONDS)

        val hits = fakeNotificationPoller.success
        val called = fakeNotificationPoller.called

        testScheduler.advanceTimeBy(pollingIntervalInSecs * 4L, TimeUnit.SECONDS)

        Assertions.assertThat(fakeNotificationPoller.success).isEqualTo(hits)
        Assertions.assertThat(fakeNotificationPoller.called).isEqualTo(called)
        Assertions.assertThat(fakeNotificationPoller.called).isEqualTo(hits + 1)
    }

    @Test
    fun `stop polling on Not Authorized error`() {
        val testScheduler = TestScheduler()
        spyOnTransformerFactoryToUseTestScheduler(testScheduler)

        uiNotificationPoller.start()

        testScheduler.advanceTimeBy(pollingIntervalInSecs * 4L, TimeUnit.SECONDS)

        Assertions.assertThat(fakeNotificationPoller.success).isGreaterThan(2)

        fakeNotificationPoller.startThrowing(HttpException(ErrorCode.NOT_AUTHORIZED))

        testScheduler.advanceTimeBy(pollingIntervalInSecs * 4L, TimeUnit.SECONDS)

        val hits = fakeNotificationPoller.success
        val called = fakeNotificationPoller.called

        testScheduler.advanceTimeBy(pollingIntervalInSecs * 4L, TimeUnit.SECONDS)

        Assertions.assertThat(fakeNotificationPoller.success).isEqualTo(hits)
        Assertions.assertThat(fakeNotificationPoller.called).isEqualTo(called)
        Assertions.assertThat(fakeNotificationPoller.called).isEqualTo(hits + 1)
    }

    @Test
    fun `keep polling on Network error`() {
        val testScheduler = TestScheduler()
        spyOnTransformerFactoryToUseTestScheduler(testScheduler)

        uiNotificationPoller.start()

        testScheduler.advanceTimeBy(pollingIntervalInSecs * 4L, TimeUnit.SECONDS)

        val hitsBeforeError = fakeNotificationPoller.success
        val attemptsBeforeError = fakeNotificationPoller.called
        Assertions.assertThat(hitsBeforeError).isGreaterThan(2)
        Assertions.assertThat(hitsBeforeError).isEqualTo(attemptsBeforeError)

        fakeNotificationPoller.startThrowing(NetworkException(RuntimeException()))

        testScheduler.advanceTimeBy(pollingIntervalInSecs * 4L, TimeUnit.SECONDS)

        val hitsAfterError = fakeNotificationPoller.success
        val attemptsAfterError = fakeNotificationPoller.called

        Assertions.assertThat(fakeNotificationPoller.success).isEqualTo(hitsAfterError)
        Assertions.assertThat(attemptsAfterError).isEqualTo(attemptsBeforeError + 1)

        Thread.sleep(400) // Give time to ui poller to handle error and re-subscribe
        testScheduler.advanceTimeBy(pollingIntervalInSecs * 4L, TimeUnit.SECONDS)

        Assertions.assertThat(fakeNotificationPoller.success).isEqualTo(hitsAfterError)
        Assertions.assertThat(fakeNotificationPoller.called).isGreaterThan(attemptsBeforeError + 1)

        fakeNotificationPoller.noMoreError()
        Thread.sleep(400) // Give time to ui poller to react

        testScheduler.advanceTimeBy(pollingIntervalInSecs * 4L, TimeUnit.SECONDS)

        Assertions.assertThat(fakeNotificationPoller.success).isGreaterThan(hitsAfterError)
        Assertions.assertThat(fakeNotificationPoller.called).isGreaterThan(attemptsAfterError)
    }

    private fun spyOnTransformerFactoryToUseTestScheduler(testScheduler: TestScheduler) {
        whenever(executionTransformerFactorySpy.getAsyncExecutor<Any>())
            .doReturn(Observable.Transformer { observable: Observable<Any> ->
                return@Transformer observable.subscribeOn(testScheduler)
                    .observeOn(Schedulers.newThread())
            })
        whenever(executionTransformerFactorySpy.backgroundScheduler)
            .doReturn(testScheduler)
    }
}