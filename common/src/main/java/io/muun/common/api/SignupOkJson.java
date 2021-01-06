package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SignupOkJson {

    @NotNull
    public PublicKeyJson cosigningPublicKey;

    @NotNull
    public PublicKeyJson swapServerPublicKey;

    /**
     * Json constructor.
     */
    public SignupOkJson() {
    }

    /**
     * Manual constructor.
     */
    public SignupOkJson(PublicKeyJson cosigningPublicKey, PublicKeyJson swapServerPublicKey) {
        this.cosigningPublicKey = cosigningPublicKey;
        this.swapServerPublicKey = swapServerPublicKey;
    }
}
