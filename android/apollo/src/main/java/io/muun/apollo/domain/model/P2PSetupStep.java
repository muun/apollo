package io.muun.apollo.domain.model;

public enum P2PSetupStep {
    // NOTE: order of definition may not match UI order of presentation.

    PHONE,
    CONFIRM_PHONE, //aka VERIFICATION_CODE
    PROFILE,
    SYNC_CONTACTS,
    FINISHED
}