package io.muun.common.api;

import io.muun.common.dates.MuunZonedDateTime;
import io.muun.common.model.HardwareWalletBrand;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.hibernate.validator.constraints.NotEmpty;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class HardwareWalletJson {

    @Nullable
    public Long id;

    @NotNull
    public HardwareWalletBrand brand;

    @NotEmpty
    public String model;

    @NotEmpty
    public String label;

    @NotNull
    public PublicKeyJson publicKey;

    @NotNull
    public MuunZonedDateTime createdAt;

    @NotNull
    public MuunZonedDateTime lastPairedAt;

    @NotNull
    public Boolean isPaired;

    /**
     * Json constructor.
     */
    public HardwareWalletJson() {
    }

    /**
     * Manual constructor.
     */
    public HardwareWalletJson(Long id,
                              @NotNull HardwareWalletBrand brand,
                              @NotNull String model,
                              @NotNull String label,
                              @NotNull PublicKeyJson publicKey,
                              @NotNull MuunZonedDateTime createdAt,
                              @NotNull MuunZonedDateTime lastPairedAt,
                              boolean isPaired) {

        this.id = id;
        this.brand = brand;
        this.model = model;
        this.label = label;
        this.publicKey = publicKey;
        this.createdAt = createdAt;
        this.lastPairedAt = lastPairedAt;
        this.isPaired = isPaired;
    }
}
