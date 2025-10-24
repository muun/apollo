package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.annotation.Nonnull;
import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class VerifiableMuunKeyJson {

    @NotNull
    // Hpke-encrypted first half of the server cosigning key
    // to the user cosigning key
    public String firstHalfKeyEncryptedToClient;

    @NotNull
    // Hpke-encrypted second half of the server cosigning key
    // to the recovery code key
    public String secondHalfKeyEncryptedToRecoveryCode;

    public String proof;

    public VerifiableMuunKeyJson() {

    }

    public VerifiableMuunKeyJson(
            @Nonnull String firstHalfKeyEncryptedToClient,
            @Nonnull String secondHalfKeyEncryptedToRecoveryCode,
            String proof
    ) {
        this.firstHalfKeyEncryptedToClient = firstHalfKeyEncryptedToClient;
        this.secondHalfKeyEncryptedToRecoveryCode = secondHalfKeyEncryptedToRecoveryCode;
        this.proof = proof;
    }
}
