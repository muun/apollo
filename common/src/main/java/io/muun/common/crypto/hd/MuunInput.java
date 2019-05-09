package io.muun.common.crypto.hd;


import io.muun.common.api.MuunInputJson;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

public class MuunInput {

    @NotNull
    private final MuunOutput prevOut;

    @NotNull
    private final MuunAddress address;

    @Nullable
    private Signature userSignature;

    @Nullable
    private Signature muunSignature; // co-signed inputs only

    @Nullable
    private MuunInputSubmarineSwap submarineSwap; // submarine swap refund inputs only

    /**
     * Build from a json-serializable representation.
     */
    public static MuunInput fromJson(MuunInputJson json) {

        return new MuunInput(
                MuunOutput.fromJson(json.prevOut),
                MuunAddress.fromJson(json.address),
                json.userSignature == null ? null : Signature.fromJson(json.userSignature),
                json.muunSignature == null ? null : Signature.fromJson(json.muunSignature),
                json.submarineSwap == null ? null : MuunInputSubmarineSwap.fromJson(
                        json.submarineSwap
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
                     @Nullable Signature muunSignature,
                     @Nullable MuunInputSubmarineSwap submarineSwap) {

        this.prevOut = prevOut;
        this.address = address;
        this.userSignature = userSignature;
        this.muunSignature = muunSignature;
        this.submarineSwap = submarineSwap;
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
    public Signature getMuunSignature() {
        return muunSignature;
    }

    public void setUserSignature(Signature userSignature) {
        this.userSignature = userSignature;
    }

    public void setMuunSignature(Signature muunSignature) {
        this.muunSignature = muunSignature;
    }

    @Nullable
    public MuunInputSubmarineSwap getSubmarineSwap() {
        return submarineSwap;
    }

    public void setSubmarineSwap(@Nullable MuunInputSubmarineSwap submarineSwap) {
        this.submarineSwap = submarineSwap;
    }

    /**
     * Convert to a json-serializable representation.
     */
    public MuunInputJson toJson() {

        return new MuunInputJson(
                prevOut.toJson(),
                address.toJson(),
                userSignature == null ? null : userSignature.toJson(),
                muunSignature == null ? null : muunSignature.toJson(),
                submarineSwap == null ? null : submarineSwap.toJson()
        );
    }

    public long getLockTime() {
        return submarineSwap != null ? submarineSwap.getLockTime() : 0;
    }
}
