package io.muun.apollo.domain.utils

import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import androidx.core.os.ConfigurationCompat
import androidx.fragment.app.Fragment
import io.muun.apollo.data.net.base.NetworkException
import io.muun.apollo.data.net.base.TlsVerificationError
import io.muun.apollo.data.os.secure_storage.SecureStorageProvider.KeyStoreCorruptedError
import io.muun.apollo.data.os.secure_storage.SecureStorageProvider.SecureStorageNoSuchElementError
import io.muun.apollo.domain.errors.MuunError
import io.muun.apollo.domain.errors.SecureKeyValueStoreError
import io.muun.apollo.domain.errors.SecureStorageError
import io.muun.apollo.domain.errors.WeirdIncorrectAttemptsBugError
import io.muun.apollo.domain.model.report.ErrorReport
import io.muun.common.Optional
import io.muun.common.model.Currency
import io.muun.common.rx.ObservableFn
import io.muun.common.rx.RxHelper
import io.muun.common.utils.ExceptionUtils
import libwallet.IntList
import libwallet.Libwallet
import libwallet.StringList
import rx.Observable
import java.io.Serializable
import java.net.SocketTimeoutException
import java.util.Locale
import java.util.concurrent.TimeoutException
import javax.crypto.BadPaddingException
import javax.money.Monetary
import javax.money.MonetaryException
import javax.money.UnknownCurrencyException

fun <T> Observable<T>.toVoid(): Observable<Void> =
    map(RxHelper::toVoid)

fun <T, E : Throwable> Observable<T>.replaceTypedError(cls: Class<E>, f: (E) -> Throwable) =
    compose(ObservableFn.replaceTypedError(cls, f))

fun <T, E : Throwable> Observable<T>.onTypedErrorResumeNext(
    cls: Class<E>,
    f: (E) -> Observable<T>,
) =
    compose(ObservableFn.onTypedErrorResumeNext(cls, f))

fun <T, E : Throwable> Observable<T>.onTypedErrorReturn(cls: Class<E>, f: (E) -> T) =
    compose(ObservableFn.onTypedErrorReturn(cls, f))

fun <T> Observable<T>.flatDoOnNext(f: (T) -> Observable<Void>) =
    compose(ObservableFn.flatDoOnNext(f))

fun <T, U> Observable<T>.zipWith(o1: Observable<U>) =
    Observable.zip(this, o1) { a, b -> Pair(a, b) }

fun <T, U, V> Observable<T>.zipWith(o1: Observable<U>, o2: Observable<V>) =
    Observable.zip(this, o1, o2) { a, b, c -> Triple(a, b, c) }

fun <T : Fragment> T.applyArgs(f: Bundle.() -> Unit) =
    apply {
        arguments = (arguments ?: Bundle()).apply(f)
    }


/**
 * Needed as inline reified functions can't be called from Java.
 */
fun Throwable.isInstanceOrIsCausedByUnknownCurrencyException() =
    isInstanceOrIsCausedByError<UnknownCurrencyException>()

/**
 * Needed as inline reified functions can't be called from Java.
 */
fun Throwable.isInstanceOrIsCausedByTimeoutError() =
    isInstanceOrIsCausedByError<TimeoutException>()
        || isInstanceOrIsCausedByError<SocketTimeoutException>()

/**
 * Needed as inline reified functions can't be called from Java.
 */
fun Throwable.isInstanceOrIsCausedByNetworkError() =
    isInstanceOrIsCausedByError<NetworkException>()

/**
 * Needed as inline reified functions can't be called from Java.
 */
fun Throwable.isInstanceOrIsCausedByTlsVerificationError() =
    isInstanceOrIsCausedByError<TlsVerificationError>()

/**
 * Needed as inline reified functions can't be called from Java.
 */
fun Throwable.isInstanceOrIsCausedBySecureStorageError() =
    isInstanceOrIsCausedByError<SecureStorageError>()

/**
 * Needed as inline reified functions can't be called from Java.
 *
 * Answers only that securekv is what went wrong: enough for a crash guard, and enough to tell
 * a securekv failure from a legacy one. It says nothing about which failure, so a caller that
 * branches on the kind wants [isInstanceOrIsCausedBySecureStorageCorruption] or
 * [isUnreadableSecureKeyValueStoreValue] instead.
 */
fun Throwable.isInstanceOrIsCausedBySecureKeyValueStoreError() =
    isInstanceOrIsCausedByError<SecureKeyValueStoreError>()

/**
 * Needed as inline reified functions can't be called from Java.
 */
fun Throwable.isInstanceOrIsCausedBySecureKeyValueStoreNotFound() =
    isInstanceOrIsCausedByError<SecureKeyValueStoreError.NotFound>()

/**
 * A value is stored under the key and securekv could not hand it over, so no retry of the same
 * read will do any better.
 *
 * NotFound is excluded because it is the opposite finding: there is nothing stored, which the
 * next read of the same key will keep reporting until something writes one.
 */
fun Throwable.isUnreadableSecureKeyValueStoreValue() =
    isInstanceOrIsCausedBySecureKeyValueStoreError() &&
        !isInstanceOrIsCausedBySecureKeyValueStoreNotFound()

/**
 * Needed as inline reified functions can't be called from Java.
 */
fun Throwable.isCauseByBadPaddingException() =
    isInstanceOrIsCausedByError<BadPaddingException>()

/**
 * Needed as inline reified functions can't be called from Java.
 *
 * NotFound and OperationFailed are not corruption: nothing is known to be wrong with the data.
 *
 * Where securekv already classified the failure, its answer wins. A SecureKeyValueStoreError
 * built from a legacy failure carries it as its cause, so the scan below would otherwise call
 * those cases corruption, including the two this predicate exists to leave out.
 */
fun Throwable.isInstanceOrIsCausedBySecureStorageCorruption(): Boolean {
    val storeError = getTypedClause<SecureKeyValueStoreError>().orElse(null)
        ?: return isCausedByLegacySecureStorageCorruption()

    // Exhaustive on purpose: a new subtype has to be classified here rather than default to
    // "not corruption", which would silently stop offering the logout on a corrupt store.
    return when (storeError) {
        is SecureKeyValueStoreError.DecryptionFailed,
        is SecureKeyValueStoreError.StorageFailed,
        -> true

        is SecureKeyValueStoreError.NotFound,
        is SecureKeyValueStoreError.OperationFailed,
        -> false
    }
}

/**
 * A stored value lost its keystore key, which is what a device-to-device copy leaves behind:
 * the preferences travel and the keystore keys never do. Narrower than
 * [isInstanceOrIsCausedBySecureStorageCorruption], which also counts any other corrupt value or
 * unusable store.
 *
 * When a SecureKeyValueStoreError is in the chain, only KeystoreKeyMissing counts: a legacy cause
 * wrapped in another securekv type (e.g. OperationFailed) is not a finding about the value.
 */
fun Throwable.isInstanceOrIsCausedByMissingKeystoreKey(): Boolean {
    val storeError = getTypedClause<SecureKeyValueStoreError>().orElse(null)
        ?: return isInstanceOrIsCausedByError<KeyStoreCorruptedError>()

    return storeError is SecureKeyValueStoreError.KeystoreKeyMissing
}

/**
 * A key missing from both the keystore and the preferences was never written, which securekv
 * calls NotFound. Both APIs are in use, so that condition cannot get one answer here and a
 * different one there.
 *
 * WeirdIncorrectAttemptsBugError is a lock screen attempt counter out of range. It extends
 * SecureStorageError only to carry a debug snapshot, and the store answered for the counter
 * just fine. No caller reaches this with one today: the lock screen swallows it and the pin
 * setup screen throws past any handler. It is here so that the type answers correctly whenever
 * one does.
 *
 * Everything else the legacy API raises stays corruption: it uses a single type for the read
 * and the write paths, so there is nothing finer to go on.
 */
private fun Throwable.isCausedByLegacySecureStorageCorruption(): Boolean {
    val legacyError = getTypedClause<SecureStorageError>().orElse(null) ?: return false

    return legacyError !is SecureStorageNoSuchElementError &&
        legacyError !is WeirdIncorrectAttemptsBugError
}

inline fun <reified T> Throwable.isInstanceOrIsCausedByError() =
    this is T || isCausedByError<T>()

inline fun <reified T> Throwable.isCausedByError() =
    getTypedClause<T>().isPresent

inline fun <reified T> Throwable.getTypedClause(): Optional<T> =
    ExceptionUtils.getTypedCause(this, T::class.java)

/**
 * Return the list of currencies reported by the device (based on the list of available locales
 * of the device). For debugging purposes only, it is used in our custom error report.
 * We receive the root cause ErrorReport just in case we need to report an unknown currency error,
 * to be able to attach it. As this is only used as part of an error handling, it is guaranteed
 * to exist.
 */
fun getUnsupportedCurrencies(report: ErrorReport): Array<String> =
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

fun Context.locale(): Locale =
    ConfigurationCompat.getLocales(resources.configuration)[0]!!

fun List<String>.toLibwalletModel(): StringList =
    Libwallet.newStringList().also { this.iterator().forEach(it::add) }

fun Set<Int>.toLibwalletIntList(): IntList =
    Libwallet.newIntList().also { this.iterator().forEach { v -> it.add(v.toLong()) } }

fun List<Int>.toLibwalletModel(): IntList =
    Libwallet.newIntList().also { this.iterator().forEach { v -> it.add(v.toLong()) } }