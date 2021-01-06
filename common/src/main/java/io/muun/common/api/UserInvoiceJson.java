package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserInvoiceJson {

    @NotEmpty
    public String paymentHashHex;

    public long shortChannelId;

    @NotNull
    public PublicKeyJson userPublicKey;

    @NotNull
    public PublicKeyJson muunPublicKey;

    @NotNull
    public PublicKeyJson identityPubKey;

    /**
     * JSON constructor.
     */
    public UserInvoiceJson() {
    }

    /**
     * Apollo constructor.
     */
    public UserInvoiceJson(final String paymentHashHex,
                           final long shortChannelId,
                           final PublicKeyJson userPublicKey,
                           final PublicKeyJson muunPublicKey,
                           final PublicKeyJson identityPubKey) {
        this.paymentHashHex = paymentHashHex;
        this.shortChannelId = shortChannelId;
        this.userPublicKey = userPublicKey;
        this.muunPublicKey = muunPublicKey;
        this.identityPubKey = identityPubKey;
    }
}
