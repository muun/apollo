package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChallengeUpdateJson {

    @NotNull
    public String uuid;

    @NotNull
    public ChallengeSetupJson challengeSetup;

    public ChallengeUpdateJson() { }

    /**
     * Constructor.
     */
    public ChallengeUpdateJson(String uuid, ChallengeSetupJson challengeSetup) {
        this.uuid = uuid;
        this.challengeSetup = challengeSetup;
    }
}
