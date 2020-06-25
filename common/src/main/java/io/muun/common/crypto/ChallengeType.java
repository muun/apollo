package io.muun.common.crypto;


import io.muun.common.exception.MissingCaseError;

public enum ChallengeType {

    /**
     * Client-generated public key before any other key was set up.
     */
    ANON(false),

    /**
     * User-provided password public key will be used to sign Challenge.
     */
    PASSWORD(true),

    /**
     * User-provided recovery code public key will be used to sign Challenge.
     */
    RECOVERY_CODE(true);

    private static final int ANON_CHALLENGE_VERSION = 1;
    private static final int PASSWORD_CHALLENGE_VERSION = 1;
    private static final int RECOVERY_CODE_CHALLENGE_VERSION = 1;

    /**
     * Whether this challenge is used to access and encrypt/decrypt a PrivateKey.
     */
    public final boolean encryptsPrivateKey;

    ChallengeType(boolean encryptsPrivateKey) {
        this.encryptsPrivateKey = encryptsPrivateKey;
    }


    /**
     * Get the current version of a challenge type.
     */
    public static int getVersion(ChallengeType type) {
        switch (type) {
            case ANON:
                return ANON_CHALLENGE_VERSION;

            case PASSWORD:
                return PASSWORD_CHALLENGE_VERSION;

            case RECOVERY_CODE:
                return RECOVERY_CODE_CHALLENGE_VERSION;

            default:
                throw new MissingCaseError(type);
        }
    }
}
