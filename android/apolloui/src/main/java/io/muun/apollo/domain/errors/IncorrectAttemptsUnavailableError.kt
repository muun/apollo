package io.muun.apollo.domain.errors

/**
 * The lock screen's incorrect attempt counter could not be read or written.
 *
 * It gates the unlock but says nothing about the pin the user typed, so the lock screen logs it
 * and keeps the lock in place. Without this type it arrives as the same [SecureKeyValueStoreError]
 * the pin read raises, and the lock screen cannot tell a pin it will never read from a counter it
 * could not update.
 *
 * Not a [SecureKeyValueStoreError] subtype: that hierarchy is sealed and a new member forces a
 * branch in the corruption predicate and in the gomobile bridge's status mapping, neither of
 * which has anything to say about the counter.
 *
 * Not a [SecureStorageError] either, even though the crash guards catch that union: both of its
 * constructors need a debug snapshot, and building one parses the MODE preference, which is one
 * of the very failures this type wraps.
 */
class IncorrectAttemptsUnavailableError(cause: SecureKeyValueStoreError) : MuunError(cause) {

    override val classification = ErrorClassification.UNEXPECTED
}
