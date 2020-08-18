package io.muun.apollo.domain.utils

import android.os.Bundle
import androidx.fragment.app.Fragment
import io.muun.apollo.domain.errors.UnknownCurrencyForLocaleError
import io.muun.common.model.Currency
import io.muun.common.rx.ObservableFn
import io.muun.common.rx.RxHelper
import rx.Observable
import timber.log.Timber
import java.util.*
import javax.money.Monetary
import javax.money.MonetaryException

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

/**
 * Return the list of currencies reported by the device (based on the list of available locales
 * of the device). For debugging purposes only, it is used in our custom error report.
 * We receive the root cause error just in case we need to report an unknown currency error, to be
 * able to attach it. As this is only used as part of an error handling, it is guaranteed to exist.
 */
fun getUnsupportedCurrencies(cause: Throwable): Array<String> {
    val availableLocales = Locale.getAvailableLocales()
    val unsupportedCurrencies: MutableSet<String> = HashSet()

    for (i in availableLocales.indices) {

        val locale = availableLocales[i]

        try {
            val currencyCode = Monetary.getCurrency(locale).currencyCode
            if (!Currency.getInfo(currencyCode).isPresent) {
                unsupportedCurrencies.add(currencyCode)
            }

        } catch (e: MonetaryException) {
            Timber.e(UnknownCurrencyForLocaleError(locale, cause))
        }
    }

    return unsupportedCurrencies.toTypedArray()
}