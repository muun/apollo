package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class StartEmailSetupJson {

    @NotEmpty
    public String email;

    @NotNull
    public ChallengeSignatureJson challengeSignature;


    /**
     * Json constructor.
     */
    public StartEmailSetupJson() {
    }

    /**
     * Code constructor.
     */
    public StartEmailSetupJson(String email, ChallengeSignatureJson challengeSignature) {
        this.email = email;
        this.challengeSignature = challengeSignature;
    }
}
