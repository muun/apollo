package io.muun.apollo.presentation.app

import org.javamoney.moneta.internal.PriorityAwareServiceProvider
import timber.log.Timber
import javax.money.spi.Bootstrap
import javax.money.spi.CurrencyProviderSpi

/**
 * Moneta's default service provider, running every lookup with the app's class loader.
 *
 * Moneta finds its services with `ServiceLoader.load(type)`, which uses the calling thread's
 * context class loader. Threads attached to the JVM from native code (such as libwallet's Go
 * threads, attached by gomobile) have none, so ServiceLoader falls back to the system class
 * loader, which can't see the app's classes, and Moneta caches that empty result for the whole
 * process. This provider runs every lookup with the app's class loader instead.
 *
 * Install it with [install] before anything uses Moneta.
 */
class MoneyServiceProvider(
    private val appClassLoader: ClassLoader,
) : PriorityAwareServiceProvider() {

    override fun <T> getServices(serviceType: Class<T>): List<T> {
        val thread = Thread.currentThread()
        val previousClassLoader = thread.contextClassLoader

        thread.contextClassLoader = appClassLoader
        try {
            return super.getServices(serviceType)
        } finally {
            thread.contextClassLoader = previousClassLoader
        }
    }

    companion object {

        /**
         * Install a MoneyServiceProvider as Moneta's service provider and build Moneta's
         * currency providers. Must run before any other thread uses Moneta.
         */
        @JvmStatic
        fun install(appClassLoader: ClassLoader) {
            val previousProvider = Bootstrap.init(MoneyServiceProvider(appClassLoader))
            if (previousProvider != null) {
                Timber.e(
                    "Moneta was used before MoneyServiceProvider was installed, replaced %s",
                    previousProvider.javaClass.name
                )
            }

            // Build Moneta's currency providers now, while only this thread uses Moneta.
            // JDKCurrencyProvider fills a static, unsynchronized HashMap in its constructor, so two
            // threads that use Moneta for the first time at once can miss currencies that exist
            // (UnknownCurrencyException for USD at startup).
            Bootstrap.getServices(CurrencyProviderSpi::class.java)
        }
    }
}
