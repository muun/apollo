package io.muun.apollo.domain.model;

public enum SignupStep {
    // NOTE: order of definition may not match UI order of presentation.

    START,
    EMAIL,
    EXISTING_PASSWORD,
    NEW_PASSWORD,
    WAITING_FOR_EMAIL_VERIFICATION,
    SYNC,
    FORGOT_PASSWORD
}
