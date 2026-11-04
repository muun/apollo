package io.muun.apollo.data.preferences

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.muun.apollo.BaseTest
import io.muun.apollo.data.external.Gen
import io.muun.apollo.data.serialization.SerializationUtils
import io.muun.apollo.domain.libwallet.LibwalletClient
import io.muun.apollo.domain.model.ExchangeRateWindow
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.threeten.bp.ZonedDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import rx.observers.TestSubscriber

class ExchangeRateWindowRepositoryTest : BaseTest() {

    private val sharedPreferences = FakeSharedPreferences()

    private val context = mockk<Context> {
        every { getSharedPreferences(any(), any()) } returns sharedPreferences
    }

    private val libwalletClient = mockk<LibwalletClient>()

    private lateinit var repository: ExchangeRateWindowRepository

    private val window = Gen.exchangeRateWindow()

    @Before
    fun setUp() {
        repository = ExchangeRateWindowRepository(context, RepositoryRegistry(), libwalletClient)
    }

    @Test
    fun `storeLatest should save the window in libwallet`() {
        // GIVEN
        every { libwalletClient.saveExchangeRateWindow(any()) } returns Unit

        // WHEN
        repository.storeLatest(window)

        // THEN
        verify { libwalletClient.saveExchangeRateWindow(window) }
    }

    @Test
    fun `storeLatest should write the window through to SharedPreferences for downgrades`() {
        // GIVEN
        every { libwalletClient.saveExchangeRateWindow(any()) } returns Unit

        // WHEN
        repository.storeLatest(window)

        // THEN
        // Older versions read this exact preference with the legacy model: assert they
        // can deserialize what we wrote.
        val savedJson = sharedPreferences.getString("window", null)
        val legacyWindow = SerializationUtils
            .deserializeJson(ExchangeRateWindow::class.java, savedJson)
        assertEquals(window.windowHid, legacyWindow.windowHid)
        assertEquals(window.rates, legacyWindow.rates)
    }

    @Test
    fun `fetch should emit the stored window on subscribe`() {
        // GIVEN
        every { libwalletClient.getExchangeRateWindow() } returns window
        val subscriber = TestSubscriber<ExchangeRateWindow>()

        // WHEN
        repository.fetch().subscribe(subscriber)

        // THEN
        subscriber.assertNoErrors()
        assertEquals(listOf(window.windowHid), subscriber.onNextEvents.map { it.windowHid })
    }

    @Test
    fun `fetch should emit every window stored after subscribing`() {
        // GIVEN
        every { libwalletClient.getExchangeRateWindow() } returns window
        every { libwalletClient.saveExchangeRateWindow(any()) } returns Unit
        val first = ExchangeRateWindow(2, ZonedDateTime.now(), mapOf("USD" to 10.0))
        val second = ExchangeRateWindow(3, ZonedDateTime.now(), mapOf("USD" to 20.0))
        val subscriber = TestSubscriber<ExchangeRateWindow>()
        repository.fetch().subscribe(subscriber)

        // WHEN
        repository.storeLatest(first)
        repository.storeLatest(second)

        // THEN
        subscriber.assertNoErrors()
        assertEquals(
            listOf(window.rates, first.rates, second.rates),
            subscriber.onNextEvents.map { it.rates }
        )
    }

    @Test
    fun `fetch should error when no window is stored`() {
        // GIVEN
        every { libwalletClient.getExchangeRateWindow() } returns null
        val subscriber = TestSubscriber<ExchangeRateWindow>()

        // WHEN
        repository.fetch().subscribe(subscriber)

        // THEN
        assertEquals(1, subscriber.onErrorEvents.size)
        assertTrue(subscriber.onNextEvents.isEmpty())
    }

    @Test
    fun `fetchOne should throw when no window is stored`() {
        // GIVEN
        every { libwalletClient.getExchangeRateWindow() } returns null

        // WHEN / THEN
        assertThatThrownBy { repository.fetchOne() }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `isSet should reflect whether libwallet holds a window`() {
        // GIVEN
        every { libwalletClient.getExchangeRateWindow() } returns null andThen window

        // WHEN
        val before = repository.isSet
        val after = repository.isSet

        // THEN
        assertFalse(before)
        assertTrue(after)
    }

    @Test
    fun `every read should go to libwallet`() {
        // GIVEN
        every { libwalletClient.getExchangeRateWindow() } returns window

        // WHEN
        repository.fetchOne()
        repository.fetchOne()

        // THEN
        // No in-memory copy: libwallet is the only source of truth for reads.
        verify(exactly = 2) { libwalletClient.getExchangeRateWindow() }
    }

    @Test
    fun `should treat a libwallet read failure as no window`() {
        // GIVEN
        every { libwalletClient.getExchangeRateWindow() } throws RuntimeException("grpc down")

        // WHEN
        val isSet = repository.isSet

        // THEN
        assertFalse(isSet)
    }

    @Test
    fun `storeLatest should not emit when the libwallet write fails`() {
        // GIVEN
        every { libwalletClient.getExchangeRateWindow() } returns window
        every {
            libwalletClient.saveExchangeRateWindow(any())
        } throws RuntimeException("grpc down")
        val subscriber = TestSubscriber<ExchangeRateWindow>()
        repository.fetch().subscribe(subscriber)

        // WHEN
        repository.storeLatest(ExchangeRateWindow(2, ZonedDateTime.now(), mapOf("USD" to 99.0)))

        // THEN
        subscriber.assertNoErrors()
        assertEquals(listOf(window.rates), subscriber.onNextEvents.map { it.rates })
    }

    @Test
    fun `fetch should not re-emit a window with the same id`() {
        // GIVEN
        every { libwalletClient.getExchangeRateWindow() } returns window
        every { libwalletClient.saveExchangeRateWindow(any()) } returns Unit
        val subscriber = TestSubscriber<ExchangeRateWindow>()
        repository.fetch().subscribe(subscriber)

        // WHEN
        repository.storeLatest(window)

        // THEN
        assertEquals(listOf(window.windowHid), subscriber.onNextEvents.map { it.windowHid })
    }

    @Test
    fun `concurrent stores should leave subscribers on the window that is in storage`() {
        // GIVEN
        // Two stores overlap (a session sync composes the realtime action directly, bypassing
        // its single-flight guard). Force the interleaving write(first), write(second),
        // emit(second), emit(first) by holding the first write open until the second store
        // has written and emitted.
        val seed = ExchangeRateWindow(1, ZonedDateTime.now(), mapOf("USD" to 1.0))
        val first = ExchangeRateWindow(2, ZonedDateTime.now(), mapOf("USD" to 2.0))
        val second = ExchangeRateWindow(3, ZonedDateTime.now(), mapOf("USD" to 3.0))
        val storage = AtomicReference(seed)
        val firstWriteLanded = CountDownLatch(1)
        val releaseFirstWrite = CountDownLatch(1)
        val secondWriteDone = CountDownLatch(1)
        every { libwalletClient.getExchangeRateWindow() } answers { storage.get() }
        every { libwalletClient.saveExchangeRateWindow(any()) } answers {
            val saved = firstArg<ExchangeRateWindow>()
            storage.set(saved)
            if (saved === first) {
                firstWriteLanded.countDown()
                releaseFirstWrite.await()
            } else {
                secondWriteDone.countDown()
            }
        }
        val subscriber = TestSubscriber<ExchangeRateWindow>()
        repository.fetch().subscribe(subscriber)

        // WHEN
        val storeFirst = Thread { repository.storeLatest(first) }
        storeFirst.start()
        firstWriteLanded.await()
        val storeSecond = Thread { repository.storeLatest(second) }
        storeSecond.start()
        // Let the second store get as far as it can: past its write when stores don't
        // exclude each other, or parked on the monitor when they do.
        val deadline = System.currentTimeMillis() + 5_000
        while (secondWriteDone.count > 0 && storeSecond.state != Thread.State.BLOCKED) {
            assertTrue("second store neither wrote nor blocked", System.currentTimeMillis() < deadline)
            Thread.sleep(1)
        }
        releaseFirstWrite.countDown()
        storeFirst.join()
        storeSecond.join()

        // THEN
        subscriber.assertNoErrors()
        val lastEmitted = subscriber.onNextEvents.last().windowHid
        assertEquals(repository.fetchOne().windowHid, lastEmitted)
    }
}
