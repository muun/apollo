package io.muun.apollo.domain.utils

import io.muun.common.rx.ObservableFn
import io.muun.common.rx.RxHelper
import rx.Observable

fun <T> Observable<T>.toVoid(): Observable<Void> =
    map(RxHelper::toVoid)

fun <T, E: Throwable> Observable<T>.replaceTypedError(cls: Class<E>, f: (E) -> Throwable) =
    compose(ObservableFn.replaceTypedError(cls, f))