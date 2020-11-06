package io.muun.common.api;

import io.muun.common.Supports;
import io.muun.common.utils.Deprecated;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.hibernate.validator.constraints.NotEmpty;

import javax.annotation.Nullable;
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

    @SuppressWarnings("indentation") // That final ) is staying there, so quit bitching linter
    @Deprecated(
            atApolloVersion = Supports.ChallengeUserKey.APOLLO,
            atFalconVersion = Supports.ChallengeUserKey.FALCON
    )
    @Nullable // Mandatory for old clients
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
                                  @Nullable ChallengeSetupJson anonChallengeSetup) {
        this.client = client;
        this.gcmToken = gcmToken;
        this.primaryCurrency = primaryCurrency;
        this.basePublicKey = basePublicKey;
        this.anonChallengeSetup = anonChallengeSetup;
    }
}
