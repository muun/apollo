package io.muun.common.model.challenge;


import io.muun.common.crypto.ChallengeType;

import javax.validation.constraints.NotNull;

public class Challenge {

    @NotNull
    public ChallengeType type;

    @NotNull
    public byte[] challenge;

    @NotNull
    public byte[] salt;

    public Challenge(ChallengeType type, byte[] challenge, byte[] salt) {
        this.type = type;
        this.challenge = challenge;
        this.salt = salt;
    }
}
