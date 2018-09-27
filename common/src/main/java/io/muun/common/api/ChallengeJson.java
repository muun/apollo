package io.muun.common.api;

import io.muun.common.crypto.ChallengeType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChallengeJson {

    @NotNull
    public ChallengeType type;

    @NotNull
    public String challenge;

    @NotNull
    public String salt;

    /**
     * Json constructor.
     */
    public ChallengeJson() {
    }

    /**
     * Constructor.
     */
    public ChallengeJson(ChallengeType type, String challenge, String salt) {
        this.type = type;
        this.challenge = challenge;
        this.salt = salt;
    }
}
