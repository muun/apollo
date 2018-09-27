package io.muun.apollo.domain.model;

import io.muun.common.crypto.ChallengeType;

import javax.validation.constraints.NotNull;

public class PendingChallengeUpdate {

    @NotNull
    public final String uuid;

    @NotNull
    public final ChallengeType type;

    public PendingChallengeUpdate(String uuid, ChallengeType type) {
        this.uuid = uuid;
        this.type = type;
    }
}
