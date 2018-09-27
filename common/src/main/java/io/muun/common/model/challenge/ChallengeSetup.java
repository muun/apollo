package io.muun.common.model.challenge;

import io.muun.common.crypto.ChallengePublicKey;
import io.muun.common.crypto.ChallengeType;

import javax.validation.constraints.NotNull;

public class ChallengeSetup {

    @NotNull
    public final ChallengeType type;

    @NotNull
    public final ChallengePublicKey publicKey;

    @NotNull
    public final byte[] salt;

    @NotNull
    public final String encryptedPrivateKey;

    @NotNull
    public final int version;

    /**
     * Create a ChallengeSetup using the latest version.
     */
    public ChallengeSetup(ChallengeType type,
                          ChallengePublicKey publicKey,
                          byte[] salt,
                          String encryptedPrivateKey,
                          int version) {

        this.type = type;
        this.publicKey = publicKey;
        this.salt = salt;
        this.encryptedPrivateKey = encryptedPrivateKey;
        this.version = version;
    }
}
