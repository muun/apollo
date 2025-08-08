package io.muun.apollo.domain.errors

import android.media.MediaDrm
import io.muun.apollo.data.os.OS
import java.util.*

open class DrmProviderError(
    providerUuid: UUID,
    cause: Throwable,
) : HardwareCapabilityError("mediaDRM", cause) {

    init {
        if (OS.supportsGetSupportedCryptoSchemes()) {
            metadata["supportedCryptoSchemes.size"] = MediaDrm.getSupportedCryptoSchemes().size
        }
        metadata["providerUuid"] = providerUuid.toString()
        metadata["providerUuidVariant"] = providerUuid.variant()
        metadata["providerUuidVersion"] = providerUuid.version()

        if (providerUuid.version() != 1) {
            metadata["timeBasedUUID"] = false
        } else {
            metadata["providerUuidNode"] = providerUuid.node()
            metadata["providerUuidTimestamp"] = providerUuid.timestamp()
            metadata["providerUuidClockSequence"] = providerUuid.clockSequence()
        }
    }
}
