package io.muun.apollo.domain.model;

public enum ChangePasswordStep {

    START,
    EXISTING_PASSWORD,
    EXISTING_RECOVERY_CODE,
    WAIT_FOR_EMAIL,
    NEW_PASSWORD
}
