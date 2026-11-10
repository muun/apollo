package io.muun.apollo.presentation.app

import org.javamoney.moneta.internal.PriorityAwareServiceProvider

/**
 * Moneta's default service provider, running every lookup with the app's class loader.
 *
 * Moneta finds its services with `ServiceLoader.load(type)`, which uses the calling thread's
 * context class loader. Threads attached to the JVM from native code (such as libwallet's Go
 * threads, attached by gomobile) have none, so ServiceLoader falls back to the system class
 * loader, which can't see the app's classes, and Moneta caches that empty result for the whole
 * process. This provider runs every lookup with the app's class loader instead.
 *
 * Install it with `Bootstrap.init` before anything uses Moneta.
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
}
