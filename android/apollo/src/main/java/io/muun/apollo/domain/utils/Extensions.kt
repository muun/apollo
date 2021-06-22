package io.muun.apollo.domain.utils

import android.os.Bundle
import android.text.TextUtils
import androidx.fragment.app.Fragment
import io.muun.apollo.domain.model.report.CrashReport
import io.muun.apollo.data.net.base.NetworkException
import io.muun.apollo.domain.errors.MuunError
import io.muun.common.model.Currency
import io.muun.common.rx.ObservableFn
import io.muun.common.rx.RxHelper
import io.muun.common.utils.ExceptionUtils
import rx.Observable
import java.io.Serializable
import java.util.*
import javax.money.Monetary
import javax.money.MonetaryException

fun <T> Observable<T>.toVoid(): Observable<Void> =
    map(RxHelper::toVoid)

fun <T, E : Throwable> Observable<T>.replaceTypedError(cls: Class<E>, f: (E) -> Throwable) =
    compose(ObservableFn.replaceTypedError(cls, f))

fun <T, E : Throwable> Observable<T>.onTypedErrorResumeNext(cls: Class<E>, f: (E) -> Observable<T>) =
    compose(ObservableFn.onTypedErrorResumeNext(cls, f))

fun <T, E : Throwable> Observable<T>.onTypedErrorReturn(cls: Class<E>, f: (E) -> T) =
    compose(ObservableFn.onTypedErrorReturn(cls, f))

fun <T> Observable<T>.flatDoOnNext(f: (T) -> Observable<Void>) =
    compose(ObservableFn.flatDoOnNext(f))

fun <T, U> Observable<T>.zipWith(o1: Observable<U>) =
    Observable.zip(this, o1) { a, b -> Pair(a, b) }

fun <T, U, V> Observable<T>.zipWith(o1: Observable<U>, o2: Observable<V>) =
    Observable.zip(this, o1, o2) { a, b, c -> Triple(a, b, c) }

fun <T: Fragment> T.applyArgs(f: Bundle.() -> Unit) =
    apply {
        arguments = (arguments ?: Bundle()).apply(f)
    }

/**
 * Needed as inline reified functions can't be called from Java.
 */
fun Throwable.isInstanceOrIsCausedByNetworkError() =
    isInstanceOrIsCausedByError<NetworkException>()

inline fun <reified T> Throwable.isInstanceOrIsCausedByError() =
    this is T || isCausedByError<NetworkException>()

inline fun <reified T> Throwable.isCausedByError() =
    ExceptionUtils.getTypedCause(this, T::class.java).isPresent

/**
 * Return the list of currencies reported by the device (based on the list of available locales
 * of the device). For debugging purposes only, it is used in our custom error report.
 * We receive the root cause crashReport just in case we need to report an unknown currency error,
 * to be able to attach it. As this is only used as part of an error handling, it is guaranteed
 * to exist.
 */
fun getUnsupportedCurrencies(report: CrashReport): Array<String> =
    getUnsupportedCurrencies(report.metadata)

fun getUnsupportedCurrencies(error: MuunError): Array<String> =
    getUnsupportedCurrencies(error.metadata)

/**
 * Return the list of currencies reported by the device (based on the list of available locales
 * of the device). For debugging purposes only, it is used in our custom error report.
 * We also attach metadata to a map received as an in-out parameter.
 */
private fun getUnsupportedCurrencies(metadata: MutableMap<String, Serializable>): Array<String> {
    val availableLocales = Locale.getAvailableLocales()
    val unsupportedCurrencies: MutableSet<String> = HashSet()
    val unsupportedLocales: MutableSet<Locale> = HashSet() //For which we have no supported Currency

    for (i in availableLocales.indices) {

        val locale = availableLocales[i]

        try {
            val currencyCode = Monetary.getCurrency(locale).currencyCode
            if (!Currency.getInfo(currencyCode).isPresent) {
                unsupportedCurrencies.add(currencyCode)
            }

        } catch (e: MonetaryException) {
            unsupportedLocales.add(locale)
        }
    }

    metadata["unsupportedLocales"] = unsupportedLocales.toTypedArray().joinToString(",")

    return unsupportedCurrencies.toTypedArray()
}

fun String?.isEmpty(): Boolean =
    this == null || TextUtils.isEmpty(this)