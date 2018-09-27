package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PasswordChangeJson {

    @NotNull
    public ChallengeSignatureJson challengeSignature;

    @NotNull
    public ChallengeSetupJson newPasswordChallengeSetup;

    /**
     * Json constructor.
     */
    public PasswordChangeJson() {
    }

    /**
     * Constructor.
     */
    public PasswordChangeJson(ChallengeSignatureJson challengeSignature,
                              ChallengeSetupJson newPasswordChallengeSetup) {

        this.challengeSignature = challengeSignature;
        this.newPasswordChallengeSetup = newPasswordChallengeSetup;
    }
}
