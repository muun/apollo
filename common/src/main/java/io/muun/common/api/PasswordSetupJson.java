package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PasswordSetupJson {

    @NotEmpty
    public ChallengeSignatureJson challengeSignature;

    @NotNull
    public ChallengeSetupJson challengeSetup;


    /**
     * Json constructor.
     */
    public PasswordSetupJson() {
    }

    /**
     * Code constructor.
     */
    public PasswordSetupJson(ChallengeSignatureJson challengeSignature,
                             ChallengeSetupJson challengeSetup) {

        this.challengeSignature = challengeSignature;
        this.challengeSetup = challengeSetup;
    }
}
