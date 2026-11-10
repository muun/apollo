package io.muun.apollo.data.preferences

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.muun.apollo.BaseTest
import io.muun.apollo.data.external.Gen
import io.muun.apollo.data.serialization.SerializationUtils
import io.muun.apollo.domain.libwallet.LibwalletClient
import io.muun.apollo.domain.model.ExchangeRateWindow
import io.muun.common.Optional
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Before
import org.junit.Test
import org.threeten.bp.ZonedDateTime
import rx.Observable
import rx.observers.TestSubscriber

/**
 * Runs one scenario list against master's SharedPreferences repository and against the
 * libwallet-backed one, so every behavior the swap keeps is asserted on both, and every
 * behavior it changes is pinned explicitly per implementation.
 *
 * Not covered here: the thread emissions arrive on (master's change listener dispatches on
 * the main thread, the libwallet one emits on the storing thread; presenters observe on main
 * either way) and libwallet transport failures, which master could not have.
 */
abstract class ExchangeRateWindowRepositoryParityTest : BaseTest() {

    /**
     * Same surface for both implementations, plus test hooks to seed and wipe the underlying
     * storage the way an earlier session, a migration, or a logout would.
     */
    interface Subject {
        val isSet: Boolean
        fun fetch(): Observable<ExchangeRateWindow>
        fun fetchOne(): ExchangeRateWindow
        fun storeLatest(window: ExchangeRateWindow)
        fun storeAndFix(window: ExchangeRateWindow)
        val fixedWindow: Optional<ExchangeRateWindow>

        /** Put a window in storage without going through the repository. */
        fun seed(window: ExchangeRateWindow)

        /** Put something unparseable in storage. */
        fun seedCorrupt()

        /** Everything the logout flow does to this repository's storage. */
        fun logout()
    }

    protected abstract fun createSubject(): Subject

    // Known divergences, pinned per implementation.
    protected abstract val absentWindowError: Class<out Throwable>
    protected abstract val isSetWithCorruptDocument: Boolean

    protected lateinit var subject: Subject

    // Distinct ids, like consecutive xrates windows. Gen.exchangeRateWindow() always uses id 1.
    private val seeded = window(id = 1, usd = 1.0)
    private val first = window(id = 2, usd = 2.0)
    private val second = window(id = 3, usd = 3.0)

    @Before
    fun setUpSubject() {
        subject = createSubject()
    }

    // --- Nothing stored ---

    @Test
    fun `isSet should be false when nothing is stored`() {
        assertThat(subject.isSet).isFalse()
    }

    @Test
    fun `fetchOne should throw when nothing is stored`() {
        assertThatThrownBy { subject.fetchOne() }.isInstanceOf(absentWindowError)
    }

    @Test
    fun `fetch should error without emitting when nothing is stored`() {
        // WHEN
        val subscriber = subscribe()

        // THEN
        assertThat(subscriber.onNextEvents).isEmpty()
        assertThat(subscriber.onErrorEvents).hasSize(1)
        assertThat(subscriber.onErrorEvents.single()).isInstanceOf(absentWindowError)
    }

    // --- Window already in storage (earlier session or migration) ---

    @Test
    fun `isSet should be true when a window is in storage`() {
        subject.seed(seeded)

        assertThat(subject.isSet).isTrue()
    }

    @Test
    fun `fetchOne should return the window in storage`() {
        subject.seed(seeded)

        assertSameWindow(subject.fetchOne(), seeded)
    }

    @Test
    fun `fetch should emit the window in storage once on subscribe`() {
        subject.seed(seeded)

        val subscriber = subscribe()

        subscriber.assertNoErrors()
        assertRates(subscriber, 1.0)
    }

    // --- Store then read ---

    @Test
    fun `storeLatest should make the window readable`() {
        subject.storeLatest(first)

        assertThat(subject.isSet).isTrue()
        assertSameWindow(subject.fetchOne(), first)
    }

    @Test
    fun `storeLatest should round trip the whole window`() {
        subject.storeLatest(first)

        val loaded = subject.fetchOne()

        assertThat(loaded.windowHid).isEqualTo(first.windowHid)
        assertThat(loaded.rates).isEqualTo(first.rates)
        assertThat(loaded.fetchDate.toInstant()).isEqualTo(first.fetchDate.toInstant())
    }

    @Test
    fun `the latest store should win`() {
        subject.storeLatest(first)
        subject.storeLatest(second)

        assertSameWindow(subject.fetchOne(), second)
    }

    // --- Reactivity ---

    @Test
    fun `a subscriber should receive the stored window and then each later store in order`() {
        subject.seed(seeded)
        val subscriber = subscribe()

        subject.storeLatest(first)
        subject.storeLatest(second)

        subscriber.assertNoErrors()
        assertRates(subscriber, 1.0, 2.0, 3.0)
    }

    @Test
    fun `every subscriber should receive later stores`() {
        subject.seed(seeded)
        val one = subscribe()
        val two = subscribe()

        subject.storeLatest(first)

        assertRates(one, 1.0, 2.0)
        assertRates(two, 1.0, 2.0)
    }

    @Test
    fun `a new subscriber should only get the current window, not earlier ones`() {
        subject.storeLatest(first)
        subject.storeLatest(second)

        val subscriber = subscribe()

        assertRates(subscriber, 3.0)
    }

    @Test
    fun `an unsubscribed consumer should stop receiving stores`() {
        subject.seed(seeded)
        val subscriber = subscribe()
        subscriber.unsubscribe()

        subject.storeLatest(first)

        assertRates(subscriber, 1.0)
    }

    @Test
    fun `a subscriber that hit the absent-window error should stay terminated`() {
        val subscriber = subscribe()

        subject.storeLatest(first)

        assertThat(subscriber.onNextEvents).isEmpty()
        assertThat(subscriber.onErrorEvents).hasSize(1)
    }

    // --- Logout ---

    @Test
    fun `logout should leave no window`() {
        subject.storeLatest(first)

        subject.logout()

        assertThat(subject.isSet).isFalse()
        assertThatThrownBy { subject.fetchOne() }.isInstanceOf(absentWindowError)
    }

    @Test
    fun `logout should neither emit nor error a live subscriber`() {
        subject.seed(seeded)
        val subscriber = subscribe()

        subject.logout()

        subscriber.assertNoErrors()
        assertRates(subscriber, 1.0)
    }

    @Test
    fun `a subscriber from before logout should receive the next login's window`() {
        subject.seed(seeded)
        val subscriber = subscribe()

        subject.logout()
        subject.storeLatest(first)

        subscriber.assertNoErrors()
        assertRates(subscriber, 1.0, 2.0)
    }

    // --- Fixed window (untouched by the swap) ---

    @Test
    fun `fixedWindow should be empty until storeAndFix`() {
        assertThat(subject.fixedWindow.isPresent).isFalse()

        subject.storeAndFix(first)

        assertSameWindow(subject.fixedWindow.get(), first)
    }

    @Test
    fun `fixedWindow should not affect the latest window`() {
        subject.storeLatest(first)

        subject.storeAndFix(second)

        assertSameWindow(subject.fetchOne(), first)
    }

    @Test
    fun `logout should drop the fixed window`() {
        subject.storeAndFix(first)

        subject.logout()

        assertThat(subject.fixedWindow.isPresent).isFalse()
    }

    // --- Same window fetched again (Houston returns it within the same xrates minute) ---

    @Test
    fun `storing the same window twice should emit it once`() {
        subject.seed(seeded)
        val subscriber = subscribe()

        subject.storeLatest(first)
        subject.storeLatest(first)

        assertRates(subscriber, 1.0, 2.0)
    }

    @Test
    fun `storing the window already in storage should not emit`() {
        subject.seed(seeded)
        val subscriber = subscribe()

        subject.storeLatest(seeded)

        assertRates(subscriber, 1.0)
    }

    @Test
    fun `a window with a new id should emit after a repeated one`() {
        subject.seed(seeded)
        val subscriber = subscribe()

        subject.storeLatest(seeded)
        subject.storeLatest(first)

        assertRates(subscriber, 1.0, 2.0)
    }

    // --- Known divergences ---

    @Test
    fun `a corrupt stored document should report isSet as pinned and fail reads`() {
        subject.seedCorrupt()

        assertThat(subject.isSet).isEqualTo(isSetWithCorruptDocument)
        assertThatThrownBy { subject.fetchOne() }.isInstanceOf(Throwable::class.java)
        assertThat(subscribe().onErrorEvents).hasSize(1)
    }

    // --- Helpers ---

    private fun subscribe(): TestSubscriber<ExchangeRateWindow> {
        val subscriber = TestSubscriber<ExchangeRateWindow>()
        subject.fetch().subscribe(subscriber)
        return subscriber
    }

    private fun assertRates(subscriber: TestSubscriber<ExchangeRateWindow>, vararg usd: Double) {
        assertThat(subscriber.onNextEvents.map { it.rates["USD"] }).containsExactly(*usd.toTypedArray())
    }

    private fun window(id: Long, usd: Double) =
        ExchangeRateWindow(id, ZonedDateTime.now(), mapOf("USD" to usd))

    private fun assertSameWindow(actual: ExchangeRateWindow, expected: ExchangeRateWindow) {
        assertThat(actual.windowHid).isEqualTo(expected.windowHid)
        assertThat(actual.rates).isEqualTo(expected.rates)
        assertThat(actual.fetchDate.toInstant()).isEqualTo(expected.fetchDate.toInstant())
    }
}

class LegacyExchangeRateWindowRepositoryParityTest : ExchangeRateWindowRepositoryParityTest() {

    override val absentWindowError = IllegalArgumentException::class.java
    override val isSetWithCorruptDocument = true

    /**
     * Master-only quirk: on Android 10 and older, clear() notifies the listener per removed
     * key, so a live subscriber reads the now-null window and errors. The libwallet version
     * behaves like master on Android 11+ everywhere (nothing emitted on logout).
     */
    @Test
    fun `on Android 10 and older, logout should error a live subscriber`() {
        subject = createSubject(FakeSharedPreferences(clearNotifiesEachKey = true))
        subject.storeLatest(Gen.exchangeRateWindow("USD" to 1.0))
        val subscriber = TestSubscriber<ExchangeRateWindow>()
        subject.fetch().subscribe(subscriber)

        subject.logout()

        assertThat(subscriber.onErrorEvents).hasSize(1)
        assertThat(subscriber.onErrorEvents.single()).isInstanceOf(absentWindowError)
    }

    override fun createSubject(): Subject = createSubject(FakeSharedPreferences())

    private fun createSubject(prefs: FakeSharedPreferences): Subject {
        val context = mockk<Context> {
            every { getSharedPreferences(any(), any()) } returns prefs
        }
        val repository = LegacyExchangeRateWindowRepository(context, mockk(relaxed = true))

        return object : Subject {
            override val isSet get() = repository.isSet
            override fun fetch() = repository.fetch()
            override fun fetchOne() = repository.fetchOne()
            override fun storeLatest(window: ExchangeRateWindow) = repository.storeLatest(window)
            override fun storeAndFix(window: ExchangeRateWindow) = repository.storeAndFix(window)
            override val fixedWindow get() = repository.fixedWindow

            override fun seed(window: ExchangeRateWindow) {
                prefs.edit().putString(
                    "window",
                    SerializationUtils.serializeJson(ExchangeRateWindow::class.java, window)
                ).apply()
            }

            override fun seedCorrupt() {
                prefs.edit().putString("window", "not json").apply()
            }

            override fun logout() = repository.clear()
        }
    }
}

class LibwalletExchangeRateWindowRepositoryParityTest : ExchangeRateWindowRepositoryParityTest() {

    override val absentWindowError = IllegalStateException::class.java
    override val isSetWithCorruptDocument = false // unparseable degrades to "no window"

    override fun createSubject(): Subject {
        val prefs = FakeSharedPreferences()
        val context = mockk<Context> {
            every { getSharedPreferences(any(), any()) } returns prefs
        }
        var stored: ExchangeRateWindow? = null
        // libwallet rejects an invalid stored document with a gRPC error on read.
        var storedIsCorrupt = false
        val libwalletClient = mockk<LibwalletClient> {
            every { getExchangeRateWindow() } answers {
                if (storedIsCorrupt) {
                    throw RuntimeException("stored exchangeRateWindow is invalid")
                }
                stored
            }
            every { saveExchangeRateWindow(any()) } answers {
                stored = firstArg()
                storedIsCorrupt = false
            }
        }
        val repository = ExchangeRateWindowRepository(context, RepositoryRegistry(), libwalletClient)

        return object : Subject {
            override val isSet get() = repository.isSet
            override fun fetch() = repository.fetch()
            override fun fetchOne() = repository.fetchOne()
            override fun storeLatest(window: ExchangeRateWindow) = repository.storeLatest(window)
            override fun storeAndFix(window: ExchangeRateWindow) = repository.storeAndFix(window)
            override val fixedWindow get() = repository.fixedWindow

            override fun seed(window: ExchangeRateWindow) {
                stored = window
                storedIsCorrupt = false
            }

            override fun seedCorrupt() {
                storedIsCorrupt = true
            }

            override fun logout() {
                // Logout clears the preferences file (fixed window) and, separately,
                // wipes libwallet's storage through LibwalletClient.resetData.
                repository.clear()
                stored = null
                storedIsCorrupt = false
            }
        }
    }
}
