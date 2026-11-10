package io.muun.apollo.domain.errors

/**
 * Raised when a secure key-value store operation fails.
 *
 * Lives here rather than next to the store: it is the contract callers branch on, so it crosses
 * layers instead of belonging to the one that raises it.
 */
sealed class SecureKeyValueStoreError : MuunError {

    constructor(message: String) : super(message)

    /**
     * Lifts the cause's metadata onto the wrapper: callers branch on the securekv type and must
     * not reach through to the legacy error that produced it to read its diagnostics.
     */
    constructor(message: String, cause: Throwable) : super(message, cause) {
        if (cause is MuunError) {
            addMetadata(cause.metadata)
        }
    }

    /** A missing key is a legitimate outcome, not a defect. */
    class NotFound : SecureKeyValueStoreError("no value stored under the given key") {
        override val classification = ErrorClassification.EXPECTED
    }

    /** The stored value could not be decrypted. Retrying returns the same error. */
    open class DecryptionFailed(cause: Throwable) :
        SecureKeyValueStoreError("value could not be decrypted", cause) {
        override val classification = ErrorClassification.UNEXPECTED
    }

    /**
     * The ciphertext is there but its keystore key is gone: the dominant cause of
     * [DecryptionFailed], and the only one a caller can work around by rewriting the value.
     * A subtype rather than a sibling, so the corruption predicate and the bridge status keep
     * matching it with no new branch.
     */
    class KeystoreKeyMissing(cause: Throwable) : DecryptionFailed(cause)

    /** The storage is in a state no read or write can work around. Retrying returns the same. */
    class StorageFailed(cause: Throwable) :
        SecureKeyValueStoreError("storage unusable", cause) {
        override val classification = ErrorClassification.UNEXPECTED
    }

    /**
     * A keystore call failed, and nothing was learned about the stored value: delete, wipe and
     * the presence probe raise it. Unlike [DecryptionFailed] and [StorageFailed], it is not a
     * finding about what is stored.
     */
    class OperationFailed(cause: Throwable) :
        SecureKeyValueStoreError("operation failed", cause) {
        override val classification = ErrorClassification.UNEXPECTED
    }
}
