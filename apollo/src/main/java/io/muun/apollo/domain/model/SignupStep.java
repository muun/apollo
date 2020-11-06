package io.muun.apollo.domain.model;

public enum SignupStep {
    START,

    LOGIN_EMAIL,

    LOGIN_RECOVERY_CODE_ONLY,
    LOGIN_RECOVERY_CODE_EMAIL_AUTH,

    LOGIN_WAIT_VERIFICATION,
    LOGIN_PASSWORD,
    LOGIN_RECOVERY_CODE,

    SYNC
}
