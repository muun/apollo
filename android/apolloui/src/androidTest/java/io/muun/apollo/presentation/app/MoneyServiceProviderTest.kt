package io.muun.apollo.presentation.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import java.util.ServiceLoader
import javax.money.CurrencyQueryBuilder
import javax.money.spi.CurrencyProviderSpi

@RunWith(AndroidJUnit4::class)
class MoneyServiceProviderTest {

    private val appClassLoader =
        InstrumentationRegistry.getInstrumentation().targetContext.classLoader

    @Test
    fun shouldFindCurrencyProvidersOnThreadWithoutContextClassLoader() {
        runOnThreadWithoutContextClassLoader {
            // GIVEN a thread without a context class loader, like libwallet's Go threads
            // Sanity check of the setup, not of the provider: plain ServiceLoader finds nothing.
            assertThat(ServiceLoader.load(CurrencyProviderSpi::class.java).toList()).isEmpty()

            // WHEN the provider looks up the currency providers
            val actual = MoneyServiceProvider(appClassLoader)
                .getServices(CurrencyProviderSpi::class.java)

            // THEN it finds them, USD included, and the thread has no context class loader again
            assertThat(actual).isNotEmpty
            val usdQuery = CurrencyQueryBuilder.of().setCurrencyCodes("USD").build()
            assertThat(actual.flatMap { it.getCurrencies(usdQuery) }.map { it.currencyCode })
                .contains("USD")
            assertThat(Thread.currentThread().contextClassLoader).isNull()
        }
    }

    private fun runOnThreadWithoutContextClassLoader(block: () -> Unit) {
        var error: Throwable? = null

        val thread = Thread {
            try {
                block()
            } catch (e: Throwable) {
                error = e
            }
        }
        thread.contextClassLoader = null
        thread.start()
        thread.join()

        error?.let { throw it }
    }
}
