package io.muun.common.crypto.hd;

import io.muun.common.api.MuunInputSubmarineSwapV102Json;
import io.muun.common.utils.Encodings;

import jakarta.validation.constraints.NotNull;
import javax.annotation.Nullable;

/**
 * Additional details required to spend a MuunInput consuming a SubmarineSwap output V102.
 */
public class MuunInputSubmarineSwapV102 {

    @NotNull
    private byte[] swapPaymentHash256;

    @NotNull
    private byte[] userPublicKey;

    @NotNull
    private byte[] cosignerPublicKey;

    @NotNull
    private byte[] swapServerPublicKey;

    private int numBlocksForExpiration;

    @Nullable
    private Signature swapServerSignature;

    @Nullable
    private String swapUuid;

    /**
     * Build from a json-serializable representation.
     */
    public static MuunInputSubmarineSwapV102 fromJson(MuunInputSubmarineSwapV102Json json) {

        return new MuunInputSubmarineSwapV102(
                Encodings.hexToBytes(json.swapPaymentHash256Hex),
                Encodings.hexToBytes(json.userPublicKeyHex),
                Encodings.hexToBytes(json.muunPublicKeyHex),
                Encodings.hexToBytes(json.swapServerPublicKeyHex),
                json.numBlocksForExpiration,
                json.swapServerSignature == null
                        ? null
                        : Signature.fromJson(json.swapServerSignature),
                null
        );
    }

    /**
     * Constructor.
     */
    public MuunInputSubmarineSwapV102(
            byte[] swapPaymentHash256,
            byte[] userPublicKey,
            byte[] cosignerPublicKey,
            byte[] swapServerPublicKey,
            int numBlocksForExpiration,
            @Nullable Signature swapServerSignature,
            @Nullable String swapUuid) {

        this.swapPaymentHash256 = swapPaymentHash256;
        this.userPublicKey = userPublicKey;
        this.cosignerPublicKey = cosignerPublicKey;
        this.swapServerPublicKey = swapServerPublicKey;
        this.numBlocksForExpiration = numBlocksForExpiration;
        this.swapServerSignature = swapServerSignature;
        this.swapUuid = swapUuid;
    }

    @Nullable
    public String getSwapUuid() {
        return swapUuid;
    }

    @Nullable
    public Signature getSwapServerSignature() {
        return swapServerSignature;
    }

    public void setSwapServerSignature(@Nullable Signature swapServerSignature) {
        this.swapServerSignature = swapServerSignature;
    }

    /**
     * Convert to a json-serializable representation.
     */
    public MuunInputSubmarineSwapV102Json toJson() {

        return new MuunInputSubmarineSwapV102Json(
                Encodings.bytesToHex(swapPaymentHash256),
                Encodings.bytesToHex(userPublicKey),
                Encodings.bytesToHex(cosignerPublicKey),
                Encodings.bytesToHex(swapServerPublicKey),
                numBlocksForExpiration,
                swapServerSignature == null ? null : swapServerSignature.toJson()
        );
    }

    public byte[] getSwapPaymentHash256() {
        return swapPaymentHash256;
    }

    public byte[] getUserPublicKey() {
        return userPublicKey;
    }

    public byte[] getCosignerPublicKey() {
        return cosignerPublicKey;
    }

    /**
     * Legacy name for {@link #getCosignerPublicKey()}: apollo still calls it.
     */
    @Deprecated
    public byte[] getMuunPublicKey() {
        return cosignerPublicKey;
    }

    public byte[] getSwapServerPublicKey() {
        return swapServerPublicKey;
    }

    public int getNumBlocksForExpiration() {
        return numBlocksForExpiration;
    }
}
