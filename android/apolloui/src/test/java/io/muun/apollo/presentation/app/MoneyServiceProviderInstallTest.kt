package io.muun.apollo.presentation.app

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.net.URL
import java.net.URLClassLoader
import java.util.Collections
import java.util.Currency
import java.util.concurrent.CyclicBarrier
import javax.money.Monetary
import javax.money.UnknownCurrencyException
import javax.money.spi.Bootstrap
import javax.money.spi.ServiceProvider

/**
 * Moneta's first use isn't thread-safe: threads that use it for the first time at once can lose
 * currencies for the rest of the process (see [MoneyServiceProvider.install]).
 *
 * The race only happens on Moneta's first use, so every attempt loads the test classpath in a
 * fresh class loader, which gives Moneta empty statics, as in a newly started process. Class
 * literals below are only used for their names, which doesn't initialize Moneta in this one.
 */
class MoneyServiceProviderInstallTest {

    @Test
    fun `should find every currency when threads use Moneta for the first time at once`() {
        repeat(ATTEMPTS) { attempt ->
            FreshMoneta().use { moneta ->
                // GIVEN Moneta installed as the app does at startup
                moneta.install()

                // WHEN several threads use it for the first time at once
                val missedDuringRace = moneta.lookUpEveryCurrencyFromThreads()

                // THEN no thread missed a currency, and none is missing afterwards
                assertThat(missedDuringRace).describedAs("attempt %d", attempt).isEmpty()
                assertThat(moneta.missingCurrencies()).describedAs("attempt %d", attempt).isEmpty()
            }
        }
    }

    /**
     * Checks that the test above can see the race. If this fails, Moneta no longer has the race
     * (or this machine couldn't trigger it), and the preload in [MoneyServiceProvider.install]
     * may no longer be needed.
     */
    @Test
    fun `should lose currencies when threads use Moneta for the first time without the preload`() {
        val someAttemptLostCurrencies = (0 until MAX_CONTROL_ATTEMPTS).any {
            FreshMoneta().use { moneta ->
                // GIVEN Moneta installed without building its currency providers
                moneta.installWithoutPreload()

                // WHEN several threads use it for the first time at once
                moneta.lookUpEveryCurrencyFromThreads()

                moneta.missingCurrencies().isNotEmpty()
            }
        }

        // THEN at least one attempt lost currencies for good
        assertThat(someAttemptLostCurrencies).isTrue
    }

    /** Moneta and the app's classes, loaded again in a class loader of their own. */
    private class FreshMoneta : AutoCloseable {

        // The system class loader's parent sees the JDK but not the test classpath.
        private val loader =
            URLClassLoader(testClasspath(), ClassLoader.getSystemClassLoader().parent)

        private val getCurrency = loader.loadClass(Monetary::class.java.name)
            .getMethod("getCurrency", String::class.java, Array<String>::class.java)

        fun install() {
            loader.loadClass(MoneyServiceProvider::class.java.name)
                .getMethod("install", ClassLoader::class.java)
                .invoke(null, loader)
        }

        fun installWithoutPreload() {
            val provider = loader.loadClass(MoneyServiceProvider::class.java.name)
                .getConstructor(ClassLoader::class.java)
                .newInstance(loader)

            loader.loadClass(Bootstrap::class.java.name)
                .getMethod("init", loader.loadClass(ServiceProvider::class.java.name))
                .invoke(null, provider)
        }

        /** Returns the currencies some thread couldn't find. */
        fun lookUpEveryCurrencyFromThreads(): Set<String> {
            val missed = Collections.synchronizedSet(mutableSetOf<String>())
            val errors = Collections.synchronizedList(mutableListOf<Throwable>())
            val barrier = CyclicBarrier(THREADS)

            val threads = List(THREADS) {
                Thread {
                    try {
                        barrier.await()
                        missed.addAll(missingFrom(CURRENCY_CODES.shuffled()))
                    } catch (e: Throwable) {
                        errors.add(e)
                    }
                }
            }
            threads.forEach { it.contextClassLoader = loader }
            threads.forEach { it.start() }
            threads.forEach { it.join() }

            errors.firstOrNull()?.let { throw it }
            return missed
        }

        fun missingCurrencies(): Set<String> {
            return missingFrom(CURRENCY_CODES)
        }

        private fun missingFrom(codes: List<String>): Set<String> {
            return codes.filterNot { isKnown(it) }.toSet()
        }

        private fun isKnown(code: String): Boolean {
            try {
                getCurrency.invoke(null, code, emptyArray<String>())
                return true
            } catch (e: InvocationTargetException) {
                if (e.cause?.javaClass?.name == UnknownCurrencyException::class.java.name) {
                    return false
                }
                throw e
            }
        }

        override fun close() {
            loader.close()
        }
    }

    private companion object {
        const val ATTEMPTS = 100
        const val MAX_CONTROL_ATTEMPTS = 1000
        const val THREADS = 8

        val CURRENCY_CODES = Currency.getAvailableCurrencies().map { it.currencyCode }

        fun testClasspath(): Array<URL> {
            return System.getProperty("java.class.path")
                .split(File.pathSeparator)
                .map { File(it).toURI().toURL() }
                .toTypedArray()
        }
    }
}
