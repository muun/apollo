package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotNull;
import javax.annotation.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PublicKeySetJson {

    @NotNull
    public PublicKeyJson basePublicKey;

    @Nullable
    public PublicKeyJson baseCosigningPublicKey;

    // This field is kept for backward compatibility reasons, not needed for new mobile apps.
    @Nullable
    public PublicKeyJson baseSwapServerPublicKey;

    @Nullable
    public PublicKeyJson lightningServerPublicKey;

    @Nullable
    public ExternalAddressesRecord externalPublicKeyIndices;

    /**
     * Json constructor.
     */
    public PublicKeySetJson() {
    }

    /**
     * Apollo update constructor.
     */
    public PublicKeySetJson(PublicKeyJson basePublicKey) {
        this.basePublicKey = basePublicKey;
    }

    /**
     * Apollo IntegrityCheck constructor.
     */
    public PublicKeySetJson(PublicKeyJson basePublicKey,
                            ExternalAddressesRecord externalPublicKeyIndices) {

        this.basePublicKey = basePublicKey;
        this.externalPublicKeyIndices = externalPublicKeyIndices;
    }

    /**
     * Houston constructor.
     */
    public PublicKeySetJson(PublicKeyJson basePublicKey,
                            PublicKeyJson baseCosigningPublicKey,
                            @Nullable PublicKeyJson baseSwapServerPublicKey,
                            @Nullable PublicKeyJson lightningServerPublicKey,
                            ExternalAddressesRecord externalPublicKeyIndices) {
        this.basePublicKey = basePublicKey;
        this.baseCosigningPublicKey = baseCosigningPublicKey;
        this.baseSwapServerPublicKey = baseSwapServerPublicKey;
        this.lightningServerPublicKey = lightningServerPublicKey;
        this.externalPublicKeyIndices = externalPublicKeyIndices;
    }
}
