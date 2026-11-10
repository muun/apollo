package io.muun.apollo.data.os

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import rx.observers.TestSubscriber

@RunWith(AndroidJUnit4::class)
class ProcessLifecycleAppStateProviderTest {

    private class TestOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)

        override val lifecycle: Lifecycle
            get() = registry
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private lateinit var owner: TestOwner
    private lateinit var provider: ProcessLifecycleAppStateProvider

    @Before
    fun setUp() {
        instrumentation.runOnMainSync {
            owner = TestOwner()
        }
        provider = ProcessLifecycleAppStateProvider(owner.lifecycle)
    }

    @Test
    fun startedLifecycleEmitsForegroundOnSubscribe() {
        moveTo(Lifecycle.State.STARTED)

        val subscriber = subscribe()

        subscriber.assertValues(AppState.FOREGROUND)
        assertThat(provider.current()).isEqualTo(AppState.FOREGROUND)
    }

    @Test
    fun stoppedLifecycleEmitsNothingOnSubscribe() {
        moveTo(Lifecycle.State.CREATED)

        val subscriber = subscribe()

        subscriber.assertNoValues()
        assertThat(provider.current()).isEqualTo(AppState.BACKGROUND)
    }

    @Test
    fun stoppingAndStartingEmitBackgroundAndForeground() {
        moveTo(Lifecycle.State.STARTED)
        val subscriber = subscribe()

        handle(Lifecycle.Event.ON_STOP)
        assertThat(provider.current()).isEqualTo(AppState.BACKGROUND)

        handle(Lifecycle.Event.ON_START)
        assertThat(provider.current()).isEqualTo(AppState.FOREGROUND)

        subscriber.assertValues(AppState.FOREGROUND, AppState.BACKGROUND, AppState.FOREGROUND)
    }

    @Test
    fun unsubscribingOutsideTheMainThreadRemovesTheObserver() {
        moveTo(Lifecycle.State.STARTED)
        val subscriber = subscribe()
        assertThat(observerCount()).isEqualTo(1)

        subscriber.unsubscribe()
        instrumentation.waitForIdleSync()

        assertThat(observerCount()).isEqualTo(0)
    }

    private fun subscribe(): TestSubscriber<AppState> {
        val subscriber = TestSubscriber<AppState>()
        provider.watch().subscribe(subscriber)
        // The subscription is posted to the main thread.
        instrumentation.waitForIdleSync()
        return subscriber
    }

    private fun moveTo(state: Lifecycle.State) {
        instrumentation.runOnMainSync {
            owner.registry.currentState = state
        }
    }

    private fun handle(event: Lifecycle.Event) {
        instrumentation.runOnMainSync {
            owner.registry.handleLifecycleEvent(event)
        }
    }

    private fun observerCount(): Int {
        var count = 0
        instrumentation.runOnMainSync {
            count = owner.registry.observerCount
        }
        return count
    }
}
