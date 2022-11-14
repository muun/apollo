package io.muun.common.api;

import io.muun.common.crypto.ChallengeType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChallengeSetupJson {

    @NotNull
    public ChallengeType type = ChallengeType.PASSWORD; // COMPAT

    @NotNull
    @JsonProperty("passwordSecretPublicKey") // COMPATIBILITY
    public String publicKey;

    @NotNull
    @JsonProperty("passwordSecretSalt") // COMPATIBILITY
    public String salt;

    @Nullable
    public String encryptedPrivateKey; // nullable for COMPATIBILITY with old Apollo

    @NotNull
    public int version;

    public ChallengeSetupJson() {
    }

    /**
     * Constructor.
     */
    public ChallengeSetupJson(@Nonnull ChallengeType type,
                              @Nonnull String publicKey,
                              @Nonnull String salt,
                              @Nonnull String encryptedPrivateKey,
                              int version) {
        this.type = type;
        this.publicKey = publicKey;
        this.salt = salt;
        this.encryptedPrivateKey = encryptedPrivateKey;
        this.version = version;
    }
}
