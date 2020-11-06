package io.muun.common.api;

import io.muun.common.crypto.ChallengeType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChallengeKeyJson {

    @NotNull
    public ChallengeType type;

    @NotNull
    public String publicKey;

    @Nullable // For RC only login (it ignores the salt). NotNull otherwise.
    public String salt;

    @NotNull
    public int challengeVersion;

    /**
     * Json constructor.
     */
    public ChallengeKeyJson() {
    }

    /**
     * Apollo Constructor. Used solely for RC only login. Needs no salt.
     */
    public ChallengeKeyJson(ChallengeType type, String publicKey, int challengeVersion) {
        this.type = type;
        this.publicKey = publicKey;
        this.challengeVersion = challengeVersion;
    }

    /**
     * Houston Constructor.
     */
    public ChallengeKeyJson(ChallengeType type,
                            String publicKey,
                            @SuppressWarnings("NullableProblems") @NotNull String salt,
                            int challengeVersion) {
        this.type = type;
        this.publicKey = publicKey;
        this.salt = salt;
        this.challengeVersion = challengeVersion;
    }
}
