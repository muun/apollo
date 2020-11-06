package io.muun.common.api;

import io.muun.common.crypto.ChallengeType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChallengeJson {

    @NotNull
    public ChallengeType type;

    @NotNull
    public String challenge;

    @Nullable // For challengeType USER_KEY. NotNull for all the rest.
    public String salt;

    /**
     * Json constructor.
     */
    public ChallengeJson() {
    }

    /**
     * Constructor for salt-less ChallengeTypes (e.g USER_KEY).
     */
    public ChallengeJson(ChallengeType type, String challenge) {
        this.type = type;
        this.challenge = challenge;
    }

    /**
     * Constructor for ChallengeTypes with salt.
     */
    public ChallengeJson(ChallengeType type,
                         String challenge,
                         @SuppressWarnings("NullableProblems") @NotNull String salt) {
        this.type = type;
        this.challenge = challenge;
        this.salt = salt;
    }
}
