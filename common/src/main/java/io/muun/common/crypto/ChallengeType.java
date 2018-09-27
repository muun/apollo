package io.muun.common.crypto;


import io.muun.common.exception.MissingCaseError;

public enum ChallengeType {

    /**
     * User-provided password public key will be used to sign Challenge.
     */
    PASSWORD,

    /**
     * User-provided recovery code public key will be used to sign Challenge.
     */
    RECOVERY_CODE;

    private static final int PASSWORD_CHALLENGE_VERSION = 1;
    private static final int RECOVERY_CODE_CHALLENGE_VERSION = 1;

    /**
     * Get the current version of a challenge type.
     */
    public static int getVersion(ChallengeType type) {
        switch (type) {
            case PASSWORD:
                return PASSWORD_CHALLENGE_VERSION;

            case RECOVERY_CODE:
                return RECOVERY_CODE_CHALLENGE_VERSION;

            default:
                throw new MissingCaseError(type);
        }
    }
}
