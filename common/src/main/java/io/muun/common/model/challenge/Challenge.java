package io.muun.common.model.challenge;


import io.muun.common.crypto.ChallengeType;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

public class Challenge {

    @NotNull
    public ChallengeType type;

    @NotNull
    public byte[] challenge;

    @Nullable
    public byte[] salt;

    /**
     * Constructor.
     */
    public Challenge(ChallengeType type, byte[] challenge, @Nullable byte[] salt) {
        this.type = type;
        this.challenge = challenge;
        this.salt = salt;
    }
}
