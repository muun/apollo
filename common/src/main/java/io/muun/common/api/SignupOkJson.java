package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SignupOkJson {

    @NotNull
    public PublicKeyJson cosigningPublicKey;

    /**
     * Json constructor.
     */
    public SignupOkJson() {
    }

    /**
     * Manual constructor.
     */
    public SignupOkJson(PublicKeyJson cosigningPublicKey) {
        this.cosigningPublicKey = cosigningPublicKey;
    }
}
