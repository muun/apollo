package io.muun.apollo.domain.model

class SecurityCenter(private val user: User) {

    fun getLevel(): SecurityLevel {
        if (!user.hasPassword) {
            return SecurityLevel.ANON

        } else if (!user.hasRecoveryCode) {
            return SecurityLevel.EMAIL_PASSWORD

        } else if (!user.emergencyKitLastExportedAt.isPresent) {
            return SecurityLevel.RECOVERY_CODE
        }

        return SecurityLevel.DONE
    }
}
