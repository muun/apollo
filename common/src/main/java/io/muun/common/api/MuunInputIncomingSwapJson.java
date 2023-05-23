package io.muun.common.api;

import io.muun.common.utils.Encodings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.annotation.Nullable;
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

    @Nullable // Only present if the swap was ever FULFILLED (note: fulfillment tx can be dropped)
    public String preimageHex;

    public String htlcOutputKeyPath;

    /**
     * JSON constructor.
     */
    public MuunInputIncomingSwapJson() {
    }

    /**
     * Constructor.
     */
    public MuunInputIncomingSwapJson(
            final byte[] sphinx,
            final byte[] htlcTx,
            final byte[] swapServerPublicKey,
            final byte[] paymentHash256,
            final long expirationHeight,
            final long collectInSats,
            final String preimageHex,
            final String htlcOutputKeyPath
    ) {
        this.sphinxHex = Encodings.bytesToHex(sphinx);
        this.htlcTxHex = Encodings.bytesToHex(htlcTx);
        this.swapServerPublicKeyHex = Encodings.bytesToHex(swapServerPublicKey);
        this.paymentHash256Hex = Encodings.bytesToHex(paymentHash256);
        this.expirationHeight = expirationHeight;
        this.collectInSats = collectInSats;
        this.preimageHex = preimageHex;
        this.htlcOutputKeyPath = htlcOutputKeyPath;
    }
}
