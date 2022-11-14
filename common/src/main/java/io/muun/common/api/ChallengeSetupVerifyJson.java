package io.muun.common.api;

import io.muun.common.crypto.ChallengeType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChallengeSetupVerifyJson {

    @NotNull
    public ChallengeType type;

    @NotNull
    public String publicKey;

    /**
     * Json constructor.
     */
    public ChallengeSetupVerifyJson() {
    }

    /**
     * Houston Constructor.
     */
    public ChallengeSetupVerifyJson(ChallengeType type, String publicKey) {
        this.type = type;
        this.publicKey = publicKey;
    }
}
