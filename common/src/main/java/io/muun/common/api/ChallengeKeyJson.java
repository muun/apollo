package io.muun.common.api;

import io.muun.common.crypto.ChallengeType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChallengeKeyJson {

    @NotNull
    public ChallengeType type;

    @NotNull
    public String publicKey;

    @NotNull
    public String salt;

    /**
     * Json constructor.
     */
    public ChallengeKeyJson() {
    }

    /**
     * Constructor.
     */
    public ChallengeKeyJson(ChallengeType type, String publicKey, String salt) {
        this.type = type;
        this.publicKey = publicKey;
        this.salt = salt;
    }
}
