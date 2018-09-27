package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.money.CurrencyUnit;
import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SignupJson {

    @NotNull
    public CurrencyUnit primaryCurrency;

    @NotNull
    public PublicKeyJson basePublicKey;

    @NotNull
    public ChallengeSetupJson passwordChallengeSetup;

    /**
     * Json constructor.
     */
    public SignupJson() {
    }

    /**
     * Apollo constructor.
     */
    public SignupJson(CurrencyUnit primaryCurrency,
                      PublicKeyJson basePublicKey,
                      ChallengeSetupJson passwordChallengeSetup) {

        this.primaryCurrency = primaryCurrency;
        this.basePublicKey = basePublicKey;
        this.passwordChallengeSetup = passwordChallengeSetup;
    }
}
