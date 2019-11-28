package io.muun.common.crypto.hd;

import io.muun.common.api.MuunInputSubmarineSwapV101Json;
import io.muun.common.utils.Encodings;

import javax.validation.constraints.NotNull;

/**
 * Additional details required to spend a MuunInput consuming a SubmarineSwap output V101.
 */
public class MuunInputSubmarineSwapV101 {

    /**
     * Build from a json-serializable representation.
     */
    public static MuunInputSubmarineSwapV101 fromJson(MuunInputSubmarineSwapV101Json json) {
        return new MuunInputSubmarineSwapV101(
                json.refundAddress,
                Encodings.hexToBytes(json.swapPaymentHash256Hex),
                Encodings.hexToBytes(json.swapServerPublicKeyHex),
                json.lockTime
        );
    }

    @NotNull
    private String refundAddress;

    @NotNull
    private byte[] swapPaymentHash256;

    @NotNull
    private byte[] swapServerPublicKey;

    @NotNull
    private long lockTime;

    /**
     * Constructor.
     */
    public MuunInputSubmarineSwapV101(String refundAddress,
                                      byte[] swapPaymentHash256,
                                      byte[] swapServerPublicKey,
                                      long lockTime) {

        this.refundAddress = refundAddress;
        this.swapPaymentHash256 = swapPaymentHash256;
        this.swapServerPublicKey = swapServerPublicKey;
        this.lockTime = lockTime;
    }

    public String getRefundAddress() {
        return refundAddress;
    }

    public byte[] getSwapPaymentHash256() {
        return swapPaymentHash256;
    }

    public byte[] getSwapServerPublicKey() {
        return swapServerPublicKey;
    }

    public long getLockTime() {
        return lockTime;
    }

    /**
     * Convert to a json-serializable representation.
     */
    public MuunInputSubmarineSwapV101Json toJson() {
        return new MuunInputSubmarineSwapV101Json(
                refundAddress,
                Encodings.bytesToHex(swapPaymentHash256),
                Encodings.bytesToHex(swapServerPublicKey),
                lockTime
        );
    }
}
