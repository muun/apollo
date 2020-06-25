package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.hibernate.validator.constraints.NotEmpty;

import javax.money.CurrencyUnit;
import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateFirstSessionJson {

    @NotNull
    public ClientJson client;

    @NotEmpty
    public String gcmToken;

    @NotNull
    public CurrencyUnit primaryCurrency;

    @NotNull
    public PublicKeyJson basePublicKey;

    @NotNull
    public ChallengeSetupJson anonChallengeSetup;


    /**
     * Json constructor.
     */
    public CreateFirstSessionJson() {
    }

    /**
     * Code constructor.
     */
    public CreateFirstSessionJson(ClientJson client,
                                  String gcmToken,
                                  CurrencyUnit primaryCurrency,
                                  PublicKeyJson basePublicKey,
                                  ChallengeSetupJson anonChallengeSetup) {
        this.client = client;
        this.gcmToken = gcmToken;
        this.primaryCurrency = primaryCurrency;
        this.basePublicKey = basePublicKey;
        this.anonChallengeSetup = anonChallengeSetup;
    }
}
