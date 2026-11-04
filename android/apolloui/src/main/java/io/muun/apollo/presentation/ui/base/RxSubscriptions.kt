package io.muun.apollo.presentation.ui.base

import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.subscriptions.CompositeSubscription
import timber.log.Timber
import javax.inject.Inject
import kotlin.reflect.KFunction

/**
 * Mixin that subscribes to observables on the main thread and retains the resulting
 * subscriptions, unsubscribing them all when closed. The MVVM counterpart of
 * BasePresenter#subscribeTo.
 *
 * Mix it into a ViewModel via delegation, injecting it and passing it to the ViewModel
 * constructor so it gets closed automatically when the ViewModel is cleared:
 *
 * ```
 * class MyViewModel @AssistedInject constructor(
 *     private val service: Service,
 *     rxSubscriptions: RxSubscriptions,
 * ) : ViewModel(rxSubscriptions), RxSubscriptions by rxSubscriptions {
 *
 *     private fun fetchSomething() {
 *         service.fetchSomething()
 *             .subscribeTo(replacing = ::fetchSomething) { handleSomething(it) }
 *     }
 * }
 * ```
 */
interface RxSubscriptions : AutoCloseable {

    /**
     * Subscribe to this observable observing it on the main thread, retaining the
     * subscription until the owner is cleared.
     *
     * When [replacing] is given, any previous subscription made with the same key is
     * unsubscribed first, so concurrent executions of the same operation never race each
     * other (the last one wins). Keys are function references (e.g.
     * `replacing = ::fetchSomething`): unique, typo-proof and rename-safe.
     *
     * Errors are logged unless a custom [onError] is provided.
     */
    fun <T> Observable<T>.subscribeTo(
        replacing: KFunction<*>? = null,
        onError: (Throwable) -> Unit = { Timber.e(it) },
        onNext: (T) -> Unit,
    ): Subscription
}

class RxSubscriptionsMixin @Inject constructor() : RxSubscriptions {

    private val subscriptions = CompositeSubscription()
    private val keyedSubscriptions = mutableMapOf<KFunction<*>, Subscription>()

    override fun <T> Observable<T>.subscribeTo(
        replacing: KFunction<*>?,
        onError: (Throwable) -> Unit,
        onNext: (T) -> Unit,
    ): Subscription {
        return observeOn(AndroidSchedulers.mainThread())
            .subscribe(onNext, onError)
            .also { subscription -> retain(subscription, replacing) }
    }

    private fun retain(subscription: Subscription, key: KFunction<*>?) {
        if (key != null) {
            // CompositeSubscription.remove() also unsubscribes the removed subscription.
            keyedSubscriptions.remove(key)?.let(subscriptions::remove)
            keyedSubscriptions[key] = subscription
        }
        subscriptions.add(subscription)
    }

    override fun close() {
        keyedSubscriptions.clear()
        subscriptions.unsubscribe()
    }
}
