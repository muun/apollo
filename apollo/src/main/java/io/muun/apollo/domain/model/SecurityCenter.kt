package io.muun.apollo.domain.model

class SecurityCenter(private val user: User, private val emailSetupSkipped: Boolean) {

    fun getLevel(): SecurityLevel {

        val hasEmergencyKit = user.emergencyKitLastExportedAt.isPresent
        val didExportKeys = hasEmergencyKit || user.hasExportedKeys // retroCompat

        if (user.hasPassword && user.hasRecoveryCode && didExportKeys) {
            return SecurityLevel.DONE

        } else if (user.hasPassword && user.hasRecoveryCode) {
            return SecurityLevel.EMAIL_AND_RC

        } else if (user.hasPassword) {
            return SecurityLevel.EMAIL

        } else if (!emailSetupSkipped) {
            return SecurityLevel.ANON

        } else if (emailSetupSkipped && user.hasRecoveryCode && hasEmergencyKit) {
            return SecurityLevel.SKIPPED_EMAIL_DONE

        } else if (emailSetupSkipped && user.hasRecoveryCode) {
            return SecurityLevel.SKIPPED_EMAIL_RC

        } else if (emailSetupSkipped) {
            return SecurityLevel.SKIPPED_EMAIL_ANON
        }

        val userState =
            "{${user.hasPassword},${user.hasRecoveryCode},$hasEmergencyKit,$emailSetupSkipped}"

        throw IllegalStateException("How did this happen? $userState, ${user.hasExportedKeys}")
    }

    fun hasOldExportKeysOnly(): Boolean =
        user.hasExportedKeys && !user.hasExportedEmergencyKit()

    fun email(): String? =
        user.email.orElse(null)

    fun emailSetupSkipped(): Boolean =
        emailSetupSkipped || (user.hasRecoveryCode && !user.hasPassword)

}
