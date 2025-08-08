package io.muun.apollo.domain.errors

import io.muun.apollo.data.os.secure_storage.SecureStorageProvider

open class SecureStorageError : MuunError {

    constructor(debugSnapshot: SecureStorageProvider.DebugSnapshot) {
        attachDebugSnapshotMetadata(debugSnapshot)
    }

    constructor(t: Throwable, debugSnapshot: SecureStorageProvider.DebugSnapshot) : super(t) {
        attachDebugSnapshotMetadata(debugSnapshot)
    }

    private fun attachDebugSnapshotMetadata(snapshot: SecureStorageProvider.DebugSnapshot) {
        metadata["secureStorageMode"] = snapshot.mode.toString()
        metadata["isCompatible"] = snapshot.isCompatible
        metadata["labelsInPrefs"] = snapshot.labelsInPrefs?.joinToString(",") ?: ""
        metadata["labelsWithIvInPrefs"] = snapshot.labelsWithIvInPrefs?.joinToString(",") ?: ""
        metadata["labelsInKeystore"] = snapshot.labelsInKeystore?.joinToString(",") ?: ""
        metadata["keystoreException"] = snapshot.keystoreException.toString()
        metadata["auditTrail"] = snapshot.auditTrail?.joinToString("") ?: ""
    }
}
