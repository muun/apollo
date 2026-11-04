package io.muun.common.model.challenge;


import io.muun.common.crypto.ChallengeType;

import jakarta.validation.constraints.NotNull;
import javax.annotation.Nullable;

public class Challenge {

    @NotNull
    public final ChallengeType type;

    @NotNull
    public final byte[] challenge;

    @Nullable
    public final byte[] salt;

    /**
     * Constructor.
     */
    public Challenge(ChallengeType type, byte[] challenge, @Nullable byte[] salt) {
        this.type = type;
        this.challenge = challenge;
        this.salt = salt;
    }
}
