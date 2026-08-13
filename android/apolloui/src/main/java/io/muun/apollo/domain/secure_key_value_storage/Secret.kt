package io.muun.apollo.domain.secure_key_value_storage

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Wraps plaintext bytes received from libwallet. The buffer is zeroed when
 * [withSecret] returns, whether the callback completes normally or throws.
 * Single-use: calling [withSecret] twice throws. Concurrent callers race on
 * an [AtomicBoolean] so the loser fails fast instead of reading wiped bytes.
 *
 * Never convert the bytes to a String inside the callback. Strings are
 * immutable on the JVM and cannot be cleared.
 */
class Secret(private val bytes: ByteArray) {

    private val consumed = AtomicBoolean(false)

    fun withSecret(fn: (ByteArray) -> Unit) {
        check(consumed.compareAndSet(false, true)) { "Secret already consumed" }
        try {
            fn(bytes)
        } finally {
            bytes.fill(0)
        }
    }
}
