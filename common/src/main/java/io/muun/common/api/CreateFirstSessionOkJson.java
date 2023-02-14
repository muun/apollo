package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateFirstSessionOkJson {

    @NotNull
    public PublicKeyJson cosigningPublicKey;

    @NotNull
    public PublicKeyJson swapServerPublicKey;

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
                                    PublicKeyJson swapServerPublicKey,
                                    @Nullable String playIntegrityNonce) {

        this.user = user;
        this.cosigningPublicKey = cosigningPublicKey;
        this.swapServerPublicKey = swapServerPublicKey;
        this.playIntegrityNonce = playIntegrityNonce;
    }
}
