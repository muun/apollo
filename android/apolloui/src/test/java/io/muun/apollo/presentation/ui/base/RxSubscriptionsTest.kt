package io.muun.apollo.presentation.ui.base

import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import rx.Scheduler
import rx.android.plugins.RxAndroidPlugins
import rx.android.plugins.RxAndroidSchedulersHook
import rx.schedulers.Schedulers
import rx.subjects.PublishSubject

class RxSubscriptionsTest {

    private class TestRxAndroidSchedulerHook : RxAndroidSchedulersHook() {
        override fun getMainThreadScheduler(): Scheduler? {
            return Schedulers.trampoline()
        }
    }

    private lateinit var rxSubscriptions: RxSubscriptions

    @Before
    fun before() {
        RxAndroidPlugins.getInstance().registerSchedulersHook(TestRxAndroidSchedulerHook())
        rxSubscriptions = RxSubscriptionsMixin()
    }

    @After
    fun after() {
        RxAndroidPlugins.getInstance().reset()
    }

    // Dummy functions used as subscription keys.
    private fun fetchSomething() {}
    private fun fetchSomethingElse() {}

    @Test
    fun `subscribeTo should deliver emissions to onNext`() {
        // GIVEN
        val subject = PublishSubject.create<String>()
        val received = mutableListOf<String>()
        with(rxSubscriptions) {
            subject.subscribeTo { received.add(it) }
        }

        // WHEN
        subject.onNext("hello")

        // THEN
        assertThat(received).containsExactly("hello")
    }

    @Test
    fun `subscribeTo should route errors to onError`() {
        // GIVEN
        val subject = PublishSubject.create<String>()
        val errors = mutableListOf<Throwable>()
        with(rxSubscriptions) {
            subject.subscribeTo(onError = { errors.add(it) }) {}
        }

        // WHEN
        val boom = RuntimeException("boom")
        subject.onError(boom)

        // THEN
        assertThat(errors).containsExactly(boom)
    }

    @Test
    fun `subscribeTo should retain the subscription until close`() {
        // GIVEN
        val subject = PublishSubject.create<String>()
        val received = mutableListOf<String>()
        with(rxSubscriptions) {
            subject.subscribeTo { received.add(it) }
        }

        // WHEN
        rxSubscriptions.close()
        subject.onNext("too late")

        // THEN
        assertThat(subject.hasObservers()).isFalse()
        assertThat(received).isEmpty()
    }

    @Test
    fun `subscribeTo should cancel the in-flight subscription when replacing the same key`() {
        // GIVEN
        val firstFetch = PublishSubject.create<String>()
        val secondFetch = PublishSubject.create<String>()
        val received = mutableListOf<String>()
        with(rxSubscriptions) {
            firstFetch.subscribeTo(replacing = ::fetchSomething) { received.add(it) }
        }

        // WHEN
        with(rxSubscriptions) {
            secondFetch.subscribeTo(replacing = ::fetchSomething) { received.add(it) }
        }
        firstFetch.onNext("stale result")
        secondFetch.onNext("fresh result")

        // THEN
        assertThat(firstFetch.hasObservers()).isFalse()
        assertThat(received).containsExactly("fresh result")
    }

    @Test
    fun `subscribeTo should keep subscriptions with different keys independent`() {
        // GIVEN
        val firstFetch = PublishSubject.create<String>()
        val secondFetch = PublishSubject.create<String>()
        val received = mutableListOf<String>()

        // WHEN
        with(rxSubscriptions) {
            firstFetch.subscribeTo(replacing = ::fetchSomething) { received.add(it) }
            secondFetch.subscribeTo(replacing = ::fetchSomethingElse) { received.add(it) }
        }
        firstFetch.onNext("first")
        secondFetch.onNext("second")

        // THEN
        assertThat(received).containsExactly("first", "second")
    }

    @Test
    fun `close should cancel keyed and unkeyed subscriptions alike`() {
        // GIVEN
        val unkeyed = PublishSubject.create<String>()
        val keyed = PublishSubject.create<String>()
        with(rxSubscriptions) {
            unkeyed.subscribeTo {}
            keyed.subscribeTo(replacing = ::fetchSomething) {}
        }

        // WHEN
        rxSubscriptions.close()

        // THEN
        assertThat(unkeyed.hasObservers()).isFalse()
        assertThat(keyed.hasObservers()).isFalse()
    }

    @Test
    fun `subscribeTo should unsubscribe immediately when already closed`() {
        // GIVEN
        rxSubscriptions.close()

        // WHEN
        val subject = PublishSubject.create<String>()
        with(rxSubscriptions) { subject.subscribeTo {} }

        // THEN
        assertThat(subject.hasObservers()).isFalse()
    }

    @Test
    fun `subscribeTo should return the subscription for manual control`() {
        // GIVEN
        val subject = PublishSubject.create<String>()

        // WHEN
        val subscription = with(rxSubscriptions) { subject.subscribeTo {} }
        subscription.unsubscribe()

        // THEN
        assertThat(subject.hasObservers()).isFalse()
    }
}
