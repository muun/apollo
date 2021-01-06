package io.muun.common.api;

import io.muun.common.utils.Encodings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class MuunInputIncomingSwapJson {

    @NotNull
    public String sphinxHex;

    @NotNull
    public String htlcTxHex;

    @NotNull
    public String swapServerPublicKeyHex;

    @NotNull
    public String paymentHash256Hex;

    public long expirationHeight;

    public long collectInSats;

    /**
     * JSON constructor.
     */
    public MuunInputIncomingSwapJson() {
    }

    /**
     * Constructor.
     */
    public MuunInputIncomingSwapJson(final byte[] sphinx,
                                     final byte[] htlcTx,
                                     final byte[] swapServerPublicKey,
                                     final byte[] paymentHash256,
                                     final long expirationHeight,
                                     final long collectInSats) {
        this.sphinxHex = Encodings.bytesToHex(sphinx);
        this.htlcTxHex = Encodings.bytesToHex(htlcTx);
        this.swapServerPublicKeyHex = Encodings.bytesToHex(swapServerPublicKey);
        this.paymentHash256Hex = Encodings.bytesToHex(paymentHash256);
        this.expirationHeight = expirationHeight;
        this.collectInSats = collectInSats;
    }
}
