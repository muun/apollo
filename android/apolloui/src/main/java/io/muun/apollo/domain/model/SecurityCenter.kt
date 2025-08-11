package io.muun.apollo.domain.model

import io.muun.apollo.domain.model.user.User

class SecurityCenter(private val user: User, private val emailSetupSkipped: Boolean) {

    fun getLevel(): SecurityLevel {

        val hasSkippedEmailSetup = emailSetupSkipped()

        val hasEmergencyKit = user.emergencyKit.isPresent
        val didExportKeys = hasEmergencyKit || user.hasExportedKeys // retroCompat


        if (user.hasPassword && user.hasRecoveryCode && didExportKeys) {
            return SecurityLevel.DONE

        } else if (user.hasPassword && user.hasRecoveryCode) {
            return SecurityLevel.EMAIL_AND_RC

        } else if (user.hasPassword) {
            return SecurityLevel.EMAIL

        } else if (!hasSkippedEmailSetup) {
            return SecurityLevel.ANON

        } else if (hasSkippedEmailSetup && user.hasRecoveryCode && hasEmergencyKit) {
            return SecurityLevel.SKIPPED_EMAIL_DONE

        } else if (hasSkippedEmailSetup && user.hasRecoveryCode) {
            return SecurityLevel.SKIPPED_EMAIL_RC

        } else if (hasSkippedEmailSetup) {
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
        !user.hasPassword && emailSetupSkipped
}
