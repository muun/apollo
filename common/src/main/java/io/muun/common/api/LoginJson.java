package io.muun.common.api;

import io.muun.common.Supports;
import io.muun.common.crypto.ChallengeType;
import io.muun.common.utils.Since;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

/**
 * Specialized json for login endpoint. We previously used a ChallengeSignatureJson to decode the
 * body's request, so that's why we keep it's properties here (type and hex). We now need to also
 * the Challenge Public Key so we created this json for this custom use case.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class LoginJson {

    @NotNull
    public ChallengeType type;

    @NotNull
    @JsonProperty("hex") // COMPATIBILITY
    public String challengeSignatureHex;

    /**
     * This value is the deviceCheckToken required by iOS devices.
     */
    @Nullable
    public String deviceCheckToken;

    @Since(
            apolloVersion = Supports.UnverifiedRecoveryCodes.APOLLO,
            falconVersion = Supports.UnverifiedRecoveryCodes.FALCON
    )
    @Nullable // Before it was added. Nullable for retro compat, for new clients should be not null
    public ChallengeKeyJson challengePublicKey;

    /**
     * Json constructor.
     */
    public LoginJson() {
    }

    /**
     * Constructor.
     */
    public LoginJson(
            final ChallengeType type,
            final String challengeSignatureHex,
            @Nullable final ChallengeKeyJson challengePublicKey
    ) {
        this.type = type;
        this.challengeSignatureHex = challengeSignatureHex;
        this.challengePublicKey = challengePublicKey;
    }

    /**
     * Added only for testing purposes.
     */
    public LoginJson(
            final ChallengeType type,
            final String challengeSignatureHex,
            @Nullable final ChallengeKeyJson challengePublicKey,
            @Nullable final String deviceCheckToken
    ) {
        this.type = type;
        this.challengeSignatureHex = challengeSignatureHex;
        this.challengePublicKey = challengePublicKey;
        this.deviceCheckToken = deviceCheckToken;
    }
}
