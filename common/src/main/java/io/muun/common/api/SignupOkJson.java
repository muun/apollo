package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotNull;
import javax.annotation.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SignupOkJson {

    @NotNull
    public PublicKeyJson cosigningPublicKey;

    @Nullable
    public PublicKeyJson swapServerPublicKey;

    @Nullable
    public PublicKeyJson lightningServerPublicKey;

    /**
     * Json constructor.
     */
    public SignupOkJson() {
    }

    /**
     * Manual constructor.
     */
    public SignupOkJson(PublicKeyJson cosigningPublicKey,
                        PublicKeyJson swapServerPublicKey,
                        @Nullable PublicKeyJson lightningServerPublicKey) {
        this.cosigningPublicKey = cosigningPublicKey;
        this.swapServerPublicKey = swapServerPublicKey;
        this.lightningServerPublicKey = lightningServerPublicKey;
    }
}
