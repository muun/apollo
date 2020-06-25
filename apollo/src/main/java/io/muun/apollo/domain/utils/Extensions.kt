package io.muun.apollo.domain.utils

import android.os.Bundle
import androidx.fragment.app.Fragment
import io.muun.common.Optional
import io.muun.common.rx.ObservableFn
import io.muun.common.rx.RxHelper
import io.muun.common.utils.ExceptionUtils
import rx.Completable
import rx.Observable

fun <T> Observable<T>.toVoid(): Observable<Void> =
    map(RxHelper::toVoid)

fun <T, E: Throwable> Observable<T>.replaceTypedError(cls: Class<E>, f: (E) -> Throwable) =
    compose(ObservableFn.replaceTypedError(cls, f))

fun <T, E: Throwable> Observable<T>.onTypedErrorResumeNext(cls: Class<E>, f: (E) -> Observable<T>) =
    compose(ObservableFn.onTypedErrorResumeNext(cls, f))

fun <T, E: Throwable> Observable<T>.onTypedErrorReturn(cls: Class<E>, f: (E) -> T) =
    compose(ObservableFn.onTypedErrorReturn(cls, f))

fun <T> Observable<T>.flatDoOnNext(f: (T) -> Observable<Void>) =
    compose(ObservableFn.flatDoOnNext(f))

fun <T, U> Observable<T>.zipWith(o1: Observable<U>) =
    Observable.zip(this, o1) { a, b -> Pair(a, b) }

fun <T, U, V> Observable<T>.zipWith(o1: Observable<U>, o2: Observable<V>) =
    Observable.zip(this, o1, o2) { a, b, c -> Triple(a, b, c) }

fun Fragment.applyArgs(f: Bundle.() -> Unit) =
    apply {
        arguments = (arguments ?: Bundle()).apply(f)
    }