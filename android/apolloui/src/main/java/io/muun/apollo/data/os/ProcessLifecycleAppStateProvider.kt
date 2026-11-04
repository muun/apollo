package io.muun.apollo.data.os

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import rx.Emitter
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import javax.inject.Inject

internal class ProcessLifecycleAppStateProvider @Inject constructor(
    private val lifecycle: Lifecycle,
) : AppStateProvider {

    override fun current(): AppState =
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            AppState.FOREGROUND
        } else {
            AppState.BACKGROUND
        }

    override fun watch(): Observable<AppState> =
        Observable.create<AppState>({ emitter ->
            val observer = object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = emitter.onNext(AppState.FOREGROUND)
                override fun onStop(owner: LifecycleOwner) = emitter.onNext(AppState.BACKGROUND)
            }
            // addObserver replays the current state, so a started app emits FOREGROUND right away.
            lifecycle.addObserver(observer)
            emitter.setCancellation { lifecycle.removeObserver(observer) }
        }, Emitter.BackpressureMode.LATEST)
            // addObserver and removeObserver must run on main.
            .subscribeOn(AndroidSchedulers.mainThread())
            .unsubscribeOn(AndroidSchedulers.mainThread())
            .distinctUntilChanged()
}
