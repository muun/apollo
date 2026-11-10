package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotNull;
import javax.annotation.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateFirstSessionOkJson {

    @NotNull
    public PublicKeyJson cosigningPublicKey;

    @Nullable
    public PublicKeyJson swapServerPublicKey;

    @Nullable
    public PublicKeyJson lightningServerPublicKey;

    @NotNull
    public UserJson user;

    @Nullable
    public String playIntegrityNonce;

    /**
     * Json constructor.
     */
    public CreateFirstSessionOkJson() {
    }

    /**
     * Manual constructor.
     */
    public CreateFirstSessionOkJson(UserJson user,
                                    PublicKeyJson cosigningPublicKey,
                                    @Nullable PublicKeyJson swapServerPublicKey,
                                    @Nullable PublicKeyJson lightningServerPublicKey,
                                    @Nullable String playIntegrityNonce) {

        this.user = user;
        this.cosigningPublicKey = cosigningPublicKey;
        this.swapServerPublicKey = swapServerPublicKey;
        this.lightningServerPublicKey = lightningServerPublicKey;
        this.playIntegrityNonce = playIntegrityNonce;
    }
}
