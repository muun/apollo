package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateRcLoginSessionJson {

    @NotNull
    public ClientJson client;

    @Nullable // Nullable after Falcon 1037 before the user grants push notification permission.
    public String gcmToken;

    @NotNull
    public ChallengeKeyJson challengeKeyJson;

    /**
     * Json constructor.
     */
    public CreateRcLoginSessionJson() {
    }

    /**
     * Code constructor.
     */
    public CreateRcLoginSessionJson(ClientJson client,
                                    @Nullable String gcmToken,
                                    ChallengeKeyJson challengeKeyJson) {
        this.client = client;
        this.gcmToken = gcmToken;
        this.challengeKeyJson = challengeKeyJson;
    }
}