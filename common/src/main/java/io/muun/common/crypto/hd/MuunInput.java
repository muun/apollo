package io.muun.common.crypto.hd;


import io.muun.common.api.MuunInputJson;
import io.muun.common.utils.Encodings;

import jakarta.validation.constraints.NotNull;
import javax.annotation.Nullable;

public class MuunInput {

    @NotNull
    private final MuunOutput prevOut;

    @NotNull
    private final MuunAddress address;

    @Nullable
    private Signature userSignature;

    @Nullable
    private Signature cosignerSignature; // co-signed inputs only

    @Nullable
    private Signature lightningServerSignature; // channel inputs only

    @Nullable
    private MuunInputSubmarineSwapV101 submarineSwap; // submarine swap V101 refund inputs only

    @Nullable
    private MuunInputSubmarineSwapV102 submarineSwapV102; // submarine swap V102 refund inputs only

    @Nullable
    private MuunInputIncomingSwap incomingSwap; // for incoming swap inputs only

    @Nullable
    private byte[] rawUserPublicNonce; // musig inputs only. Set by user.

    @Nullable
    private byte[] rawCosignerPublicNonce; // musig inputs only. Set by cosigner.

    // NOTE: exists only for testing capabilities. DO NOT EVER assume its existence or try to use it
    @Nullable
    private byte[] userSessionId; // musig inputs only

    /**
     * Build from a json-serializable representation.
     */
    public static MuunInput fromJson(MuunInputJson json) {

        return new MuunInput(
                MuunOutput.fromJson(json.prevOut),
                MuunAddress.fromJson(json.address),
                json.userSignature == null ? null : Signature.fromJson(json.userSignature),
                json.muunSignature == null ? null : Signature.fromJson(json.muunSignature),
                json.swapServerSignature == null ? null : Signature.fromJson(
                        json.swapServerSignature
                ),
                json.submarineSwap == null ? null : MuunInputSubmarineSwapV101.fromJson(
                        json.submarineSwap
                ),
                json.submarineSwapV102 == null ? null : MuunInputSubmarineSwapV102.fromJson(
                        json.submarineSwapV102
                ),
                json.incomingSwap == null ? null : MuunInputIncomingSwap.fromJson(
                        json.incomingSwap
                ),
                json.rawMuunPublicNonceHex == null ? null : Encodings.hexToBytes(
                        json.rawMuunPublicNonceHex
                )
        );
    }

    /**
     * Constructor without signatures or additional details.
     */
    public MuunInput(MuunOutput prevOut, MuunAddress address) {
        this.prevOut = prevOut;
        this.address = address;
    }

    /**
     * Full constructor.
     */
    public MuunInput(MuunOutput prevOut,
                     MuunAddress address,
                     @Nullable Signature userSignature,
                     @Nullable Signature cosignerSignature,
                     @Nullable Signature lightningServerSignature,
                     @Nullable MuunInputSubmarineSwapV101 submarineSwap,
                     @Nullable MuunInputSubmarineSwapV102 submarineSwapV102,
                     @Nullable MuunInputIncomingSwap incomingSwap,
                     @Nullable byte[] rawCosignerPublicNonce) {

        this.prevOut = prevOut;
        this.address = address;
        this.userSignature = userSignature;
        this.cosignerSignature = cosignerSignature;
        this.lightningServerSignature = lightningServerSignature;
        this.submarineSwap = submarineSwap;
        this.submarineSwapV102 = submarineSwapV102;
        this.incomingSwap = incomingSwap;
        this.rawCosignerPublicNonce = rawCosignerPublicNonce;
    }

    public MuunOutput getPrevOut() {
        return prevOut;
    }

    public int getVersion() {
        return address.getVersion();
    }

    public String getDerivationPath() {
        return address.getDerivationPath();
    }

    public MuunAddress getAddress() {
        return address;
    }

    @Nullable
    public Signature getUserSignature() {
        return userSignature;
    }

    @Nullable
    public Signature getCosignerSignature() {
        return cosignerSignature;
    }

    /**
     * Legacy name for {@link #getCosignerSignature()}: apollo still calls it.
     */
    @Deprecated
    @Nullable
    public Signature getMuunSignature() {
        return cosignerSignature;
    }

    @Nullable
    public Signature getLightningServerSignature() {
        return lightningServerSignature;
    }

    public void setUserSignature(Signature userSignature) {
        this.userSignature = userSignature;
    }

    public void setCosignerSignature(Signature cosignerSignature) {
        this.cosignerSignature = cosignerSignature;
    }

    public void setLightningServerSignature(Signature lightningServerSignature) {
        this.lightningServerSignature = lightningServerSignature;
    }

    @Nullable
    public MuunInputSubmarineSwapV101 getSubmarineSwap() {
        return submarineSwap;
    }

    public void setSubmarineSwap(@Nullable MuunInputSubmarineSwapV101 submarineSwap) {
        this.submarineSwap = submarineSwap;
    }

    @Nullable
    public MuunInputSubmarineSwapV102 getSubmarineSwapV102() {
        return submarineSwapV102;
    }

    public void setSubmarineSwapV102(@Nullable MuunInputSubmarineSwapV102 submarineSwapV102) {
        this.submarineSwapV102 = submarineSwapV102;
    }

    @Nullable
    public MuunInputIncomingSwap getIncomingSwap() {
        return incomingSwap;
    }

    public void setIncomingSwap(@Nullable MuunInputIncomingSwap incomingSwap) {
        this.incomingSwap = incomingSwap;
    }

    @Nullable
    public byte[] getRawUserPublicNonce() {
        return rawUserPublicNonce;
    }

    public void setRawCosignerPublicNonce(@Nullable byte[] rawCosignerPublicNonce) {
        this.rawCosignerPublicNonce = rawCosignerPublicNonce;
    }

    @Nullable
    public byte[] getRawCosignerPublicNonce() {
        return rawCosignerPublicNonce;
    }

    /**
     * Legacy name for {@link #getRawCosignerPublicNonce()}: apollo still calls it.
     */
    @Deprecated
    @Nullable
    public byte[] getRawMuunPublicNonce() {
        return rawCosignerPublicNonce;
    }

    public void setRawUserPublicNonce(@Nullable byte[] rawUserPublicNonce) {
        this.rawUserPublicNonce = rawUserPublicNonce;
    }

    @Nullable
    public byte[] getUserSessionId() {
        return userSessionId;
    }

    public void setUserSessionId(@Nullable byte[] userSessionId) {
        this.userSessionId = userSessionId;
    }

    /**
     * Convert to a json-serializable representation.
     */
    public MuunInputJson toJson() {

        return new MuunInputJson(
                prevOut.toJson(),
                address.toJson(),
                userSignature == null ? null : userSignature.toJson(),
                cosignerSignature == null ? null : cosignerSignature.toJson(),
                lightningServerSignature == null ? null : lightningServerSignature.toJson(),
                submarineSwap == null ? null : submarineSwap.toJson(),
                submarineSwapV102 == null ? null : submarineSwapV102.toJson(),
                incomingSwap == null ? null : incomingSwap.toJson(),
                rawCosignerPublicNonce == null
                        ? null : Encodings.bytesToHex(rawCosignerPublicNonce)
        );
    }

    public long getLockTime() {
        return submarineSwap != null ? submarineSwap.getLockTime() : 0;
    }
}
