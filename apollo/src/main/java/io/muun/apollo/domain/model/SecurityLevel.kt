package io.muun.apollo.domain.model

enum class SecurityLevel {
    ANON,               // NO EMAIL, NO PASSWORD, NO RC, NO EXPORT KEYS
    EMAIL_PASSWORD,     // NO RC, NO EXPORT KEYS
    RECOVERY_CODE,      // NO EXPORT KEYS
    DONE;               // EVERYTHING SET UP!

    fun nextStep(): NextStep {
        return when(this) {
            ANON -> NextStep.SET_UP_EMAIL
            EMAIL_PASSWORD -> NextStep.SET_UP_RECOVERY_CODE
            RECOVERY_CODE -> NextStep.EXPORT_KEYS
            DONE -> NextStep.FULLY_SET
        }
    }
}

enum class NextStep {
    SET_UP_EMAIL,
    SET_UP_RECOVERY_CODE,
    EXPORT_KEYS,
    FULLY_SET
}
