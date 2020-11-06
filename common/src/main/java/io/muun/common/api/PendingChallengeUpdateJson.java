package io.muun.common.api;

import io.muun.common.crypto.ChallengeType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PendingChallengeUpdateJson {

    @NotNull
    public String uuid;

    @NotNull
    public ChallengeType type;

    public PendingChallengeUpdateJson() {
    }

    /**
     * Constructor.
     */
    public PendingChallengeUpdateJson(String uuid, ChallengeType type) {
        this.uuid = uuid;
        this.type = type;
    }
}
