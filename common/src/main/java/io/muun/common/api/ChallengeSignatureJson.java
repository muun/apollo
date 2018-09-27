package io.muun.common.api;

import io.muun.common.crypto.ChallengeType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotNull;


@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChallengeSignatureJson {

    @NotNull
    public ChallengeType type;

    @NotNull
    public String hex;

    /**
     * Json constructor.
     */
    public ChallengeSignatureJson() {
    }

    /**
     * Constructor.
     */
    public ChallengeSignatureJson(ChallengeType type, String hex) {
        this.type = type;
        this.hex = hex;
    }
}
