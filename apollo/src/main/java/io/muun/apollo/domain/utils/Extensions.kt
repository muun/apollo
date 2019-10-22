package io.muun.apollo.domain.utils

import android.os.Bundle
import androidx.fragment.app.Fragment
import io.muun.common.rx.ObservableFn
import io.muun.common.rx.RxHelper
import rx.Observable

fun <T> Observable<T>.toVoid(): Observable<Void> =
    map(RxHelper::toVoid)

fun <T, E: Throwable> Observable<T>.replaceTypedError(cls: Class<E>, f: (E) -> Throwable) =
    compose(ObservableFn.replaceTypedError(cls, f))


fun Fragment.applyArgs(f: Bundle.() -> Unit) =
    apply {
        arguments = (arguments ?: Bundle()).apply(f)
    }