package io.muun.apollo.domain.model

import org.threeten.bp.ZoneOffset
import org.threeten.bp.ZonedDateTime

class EmergencyKitExport(
    private val info: GeneratedEmergencyKitInfo,
    val isVerified: Boolean,
    val method: Method,
    val exportedAt: ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC)
) {

    fun getKitVersion(): Int {
        return info.version
    }

    fun getVerificationCode(): String {
        return info.verificationCode
    }

    enum class Method {
        UNKNOWN,
        DRIVE,
        MANUAL,
        ICLOUD // Can't be exported via Apollo but Falcon users can sign-in in Apollo
    }
}