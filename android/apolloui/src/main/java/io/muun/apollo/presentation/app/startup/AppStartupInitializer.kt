package io.muun.apollo.presentation.app.startup

import io.muun.apollo.presentation.app.di.PerApplication
import rx.Completable
import rx.Single
import javax.inject.Inject

@PerApplication
class AppStartupInitializer @Inject constructor(
    private val initializers: @JvmSuppressWildcards Map<Class<out Initializer>, Initializer>,
) : Initializer {

    private val cache = mutableMapOf<Class<out Initializer>, Completable>()

    /**
     * Returns a [Completable] initialization chain that run whatever it can in parallel and the rest in series.
     */
    override fun init(): Completable {
        // TODO: Validate circular dependencies
        return initializers.keys.map(::buildCompletableForInitializer).let(Completable::merge)
    }

    /**
     * Returns a [Completable] for an [Initializer] which runs an initialization chain based on it's dependencies.
     */
    private fun buildCompletableForInitializer(clazz: Class<out Initializer>): Completable {
        cache[clazz]?.let { return it }
        val initializer = initializers.getValue(clazz)
        val dependencies = initializer.dependencies()

        val completable = if (dependencies.isEmpty()) {
            initializer.init()
        } else {
            // merge dependencies in parallel and then run initializer
            Completable.merge(dependencies.map(::buildCompletableForInitializer))
                .andThen(initializer.init())
        }

        return completable.cache().also { cachedCompletable ->
            cache[clazz] = cachedCompletable
        }
    }
}

/**
 * Converts a Completable into a cached Completable that runs its side-effects only once,
 * regardless of how many times it is subscribed.
 *
 * Why this exists:
 * - RxJava 1's Completable lacks a `.cache()` operator.
 * - Some initializers may be requested by multiple dependents.
 * - Without caching, each subscription would re-run the initialization.
 *
 * How it works:
 * 1. Convert the Completable into a Single<Unit> (so we can use Single.cache()).
 * 2. Apply Single.cache() to memoize completion.
 * 3. Convert it back into a Completable.
 *
 * Effectively equivalent to RxJava 2's `completable.cache()` semantics.
 */
fun Completable.cache(): Completable {
    return Single.defer { this.toSingle { Unit } }
        .cache()
        .flatMapCompletable { Completable.complete() }
}
