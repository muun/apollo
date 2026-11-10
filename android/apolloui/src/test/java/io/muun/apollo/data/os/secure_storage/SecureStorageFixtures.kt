package io.muun.apollo.data.os.secure_storage

import io.muun.apollo.domain.errors.SecureStorageError

/**
 * The provider attaches a debug snapshot to every error it raises, so building one is the price
 * of admission for any test that feeds a secure storage failure. Shared so that a new
 * DebugSnapshot field lands in one place.
 */
fun debugSnapshot(
    labelsWithIvInPrefs: Set<String> = emptySet(),
): SecureStorageProvider.DebugSnapshot {
    return SecureStorageProvider.DebugSnapshot(
        SecureStorageMode.M_MODE,
        /* isCompatible = */ true,
        /* labelsInPrefs = */ emptySet(),
        labelsWithIvInPrefs,
        /* labelsInKeystore = */ emptySet(),
        /* keystoreException = */ null,
        /* auditTrail = */ emptyList(),
    )
}

fun secureStorageError(cause: Throwable = RuntimeException("boom")): SecureStorageError {
    return SecureStorageError(cause, debugSnapshot())
}
