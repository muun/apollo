package io.muun.apollo.domain.model;

import io.muun.apollo.domain.model.base.HoustonIdModel;
import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.model.HardwareWalletBrand;
import io.muun.common.utils.Preconditions;

import org.threeten.bp.ZonedDateTime;

import javax.validation.constraints.NotNull;

public class HardwareWallet extends HoustonIdModel {

    @NotNull
    public final HardwareWalletBrand brand;

    @NotNull
    public final String model;

    @NotNull
    public final String label;

    @NotNull
    public final PublicKey basePublicKey;

    @NotNull
    public final ZonedDateTime createdAt;

    @NotNull
    public final ZonedDateTime lastPairedAt;

    public final boolean isPaired;

    /**
     * Constructor from plain data.
     */
    public HardwareWallet(Long id,
                          Long hid,
                          HardwareWalletBrand brand,
                          String model,
                          String label,
                          PublicKey basePublicKey,
                          ZonedDateTime createdAt,
                          ZonedDateTime lastPairedAt,
                          boolean isPaired) {

        super(id, hid);
        this.brand = brand;
        this.model = model;
        this.label = label;
        this.basePublicKey = basePublicKey;
        this.createdAt = createdAt;
        this.lastPairedAt = lastPairedAt;
        this.isPaired = isPaired;
    }

    /**
     * Constructor from Apollo.
     */
    public HardwareWallet(HardwareWalletBrand brand,
                          String model,
                          String label,
                          PublicKey basePublicKey,
                          boolean isPaired) {
        super(null, null);

        this.brand = brand;
        this.model = model;
        this.label = label;
        this.basePublicKey = basePublicKey;
        this.createdAt = ZonedDateTime.now();
        this.lastPairedAt = ZonedDateTime.now();
        this.isPaired = isPaired;
    }

    /**
     * Merge this HW with an updated copy, choosing whether to keep or replace each field.
     */
    public HardwareWallet mergeWithUpdate(HardwareWallet other) {
        Preconditions.checkArgument(basePublicKey.equals(other.basePublicKey));

        return new HardwareWallet(
                id,
                other.hid,
                other.brand,
                other.model,
                other.label,
                basePublicKey,
                other.createdAt,
                other.lastPairedAt,
                other.isPaired
        );
    }
}
