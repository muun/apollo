package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Contact {

    @NotNull
    public PublicProfileJson publicProfile;

    @NotNull
    public Integer maxAddressVersion;

    @NotNull
    public PublicKeyJson publicKey;

    @NotNull
    public PublicKeyJson cosigningPublicKey;

    @NotNull
    public Long lastDerivationIndex;

    /**
     * Json constructor.
     */
    public Contact() {
    }

    /**
     * Houston constructor.
     */
    public Contact(PublicProfileJson publicProfile,
                   Integer maxAddressVersion,
                   PublicKeyJson publicKey,
                   @Nullable PublicKeyJson cosigningPublicKey,
                   Long lastDerivationIndex) {

        this.publicProfile = publicProfile;
        this.maxAddressVersion = maxAddressVersion;
        this.publicKey = publicKey;
        this.cosigningPublicKey = cosigningPublicKey;
        this.lastDerivationIndex = lastDerivationIndex;
    }
}
