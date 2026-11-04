package io.muun.apollo.osmodules.securekv

import io.muun.apollo.domain.errors.SecureKeyValueStoreError

/**
 * Capability handle over a single key in [SecureKeyValueStore]. Holds no
 * plaintext: the bytes are fetched fresh from the store inside [withSecret] and
 * zeroed as soon as the callback returns, whether it completes or throws.
 * Reusable: every call fetches again, so nothing lingers between calls and a
 * call that failed with [SecureKeyValueStoreError.OperationFailed] can be repeated.
 *
 * Never convert the bytes to a String inside the callback. Strings are immutable
 * on the JVM and cannot be cleared.
 *
 * The same holds for anything else built from the bytes and allowed to outlive the
 * callback: this zeroes the array, not the copy that object kept. Reads that still
 * do either carry a TODO naming what has to change for them to stop.
 *
 * [withSecret] returns Unit on purpose. A generic return would let a caller lift
 * the bytes, or anything built from them, out of the callback in one clean line.
 * Getting a value out instead takes a var declared outside and assigned inside,
 * so that the escape is visible at the call site rather than hidden in a return.
 *
 * [withSecret] is inline so that neither the callback nor the vars it captures
 * are allocated on the heap. The callback is crossinline to keep the rule above:
 * a plain inline lambda would allow a non-local return, the same one-line escape.
 */
class Secret(
    private val key: String,
    private val fetchBytes: (String) -> ByteArray,
) {

    /** Only for inlining [withSecret]. Bytes read here are never zeroed. */
    @PublishedApi
    internal fun fetch(): ByteArray {
        return fetchBytes(key)
    }

    inline fun withSecret(crossinline fn: (ByteArray) -> Unit) {
        val bytes = fetch()
        try {
            fn(bytes)
        } finally {
            bytes.fill(0)
        }
    }
}
