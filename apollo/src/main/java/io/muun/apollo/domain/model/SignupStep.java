package io.muun.apollo.domain.model;

public enum SignupStep {
    START(0),

    LOGIN_EMAIL(1),
    LOGIN_WAIT_VERIFICATION(2),
    LOGIN_PASSWORD(3),
    LOGIN_RECOVERY_CODE(3),

    SIGNUP_EMAIL(1),
    SIGNUP_WAIT_VERIFICATION(2),
    SIGNUP_PASSWORD(3),

    SYNC(4);

    // For display purposes:
    public final int number;

    SignupStep(int number) {
        this.number = number;
    }

    public static final int LOGIN_STEP_COUNT = 3;
    public static final int SIGNUP_STEP_COUNT = 3;
}
