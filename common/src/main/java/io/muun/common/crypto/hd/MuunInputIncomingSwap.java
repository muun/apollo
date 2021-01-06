package io.muun.common.crypto.hd;

import io.muun.common.api.MuunInputIncomingSwapJson;
import io.muun.common.utils.Encodings;

import javax.validation.constraints.NotNull;

/**
 * Additional details required to spend a MuunInput consuming a SubmarineSwap output V101.
 */
public class MuunInputIncomingSwap {

    @NotNull
    private final byte[] sphinx;

    @NotNull
    private final byte[] htlcTx;

    @NotNull
    private final byte[] swapServerPublicKey;

    @NotNull
    private final byte[] paymentHash256;

    private final long expirationHeight;

    private final long collectInSats;

    /**
     * Convert from JSON to model.
     */
    public static MuunInputIncomingSwap fromJson(final MuunInputIncomingSwapJson json) {
        return new MuunInputIncomingSwap(
                Encodings.hexToBytes(json.sphinxHex),
                Encodings.hexToBytes(json.htlcTxHex),
                Encodings.hexToBytes(json.swapServerPublicKeyHex),
                Encodings.hexToBytes(json.paymentHash256Hex),
                json.expirationHeight,
                json.collectInSats);
    }

    /**
     * Constructor.
     */
    public MuunInputIncomingSwap(byte[] sphinx,
                                 byte[] htlcTx,
                                 byte[] swapServerPublicKey,
                                 byte[] paymentHash256,
                                 final long expirationHeight,
                                 final long collectInSats) {
        this.sphinx = sphinx;
        this.htlcTx = htlcTx;
        this.swapServerPublicKey = swapServerPublicKey;
        this.paymentHash256 = paymentHash256;
        this.expirationHeight = expirationHeight;
        this.collectInSats = collectInSats;
    }

    public byte[] getSphinx() {
        return sphinx;
    }

    public byte[] getHtlcTx() {
        return htlcTx;
    }

    public byte[] getSwapServerPublicKey() {
        return swapServerPublicKey;
    }

    public byte[] getPaymentHash256() {
        return paymentHash256;
    }

    public long getExpirationHeight() {
        return expirationHeight;
    }

    public long getCollectInSats() {
        return collectInSats;
    }

    /**
     * Convert to JSON.
     */
    public MuunInputIncomingSwapJson toJson() {
        return new MuunInputIncomingSwapJson(
                sphinx,
                htlcTx,
                swapServerPublicKey,
                paymentHash256,
                expirationHeight,
                collectInSats
        );
    }
}
