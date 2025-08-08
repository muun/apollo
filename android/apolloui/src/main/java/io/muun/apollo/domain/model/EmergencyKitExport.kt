package io.muun.apollo.domain.model

import org.threeten.bp.ZoneOffset
import org.threeten.bp.ZonedDateTime

class EmergencyKitExport(
    val generatedKit: GeneratedEmergencyKit,
    val isVerified: Boolean,
    val method: Method,
    val exportedAt: ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC)
) {

    enum class Method {
        UNKNOWN,
        DRIVE,
        MANUAL,
        ICLOUD // Can't be exported via Apollo but Falcon users can sign-in in Apollo
    }

}