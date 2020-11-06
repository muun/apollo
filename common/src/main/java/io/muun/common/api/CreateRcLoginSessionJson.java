package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateRcLoginSessionJson {

    @NotNull
    public ClientJson client;

    @NotEmpty
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
                                    String gcmToken,
                                    ChallengeKeyJson challengeKeyJson) {
        this.client = client;
        this.gcmToken = gcmToken;
        this.challengeKeyJson = challengeKeyJson;
    }
}