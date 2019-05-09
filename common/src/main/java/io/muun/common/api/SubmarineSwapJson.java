package io.muun.common.api;

import io.muun.common.dates.MuunZonedDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubmarineSwapJson {

    @NotNull
    public String swapUuid;

    @NotNull
    public String invoice;

    @NotNull
    public SubmarineSwapReceiverJson receiver;

    @NotNull
    public SubmarineSwapFundingOutputJson fundingOutput;

    @NotNull
    public Long sweepFeeInSatoshis;

    @NotNull
    public Long lightningFeeInSatoshis;

    @NotNull
    public MuunZonedDateTime expiresAt;

    @Nullable
    public MuunZonedDateTime payedAt;

    @Nullable
    public String preimageInHex;

    /**
     * Json constructor.
     */
    public SubmarineSwapJson() {
    }

    /**
     * Manual constructor.
     */
    public SubmarineSwapJson(String swapUuid,
                             String invoice,
                             SubmarineSwapReceiverJson receiver,
                             SubmarineSwapFundingOutputJson fundingOutput,
                             Long sweepFeeInSatoshis,
                             Long lightningFeeInSatoshis,
                             MuunZonedDateTime expiresAt,
                             @Nullable MuunZonedDateTime payedAt,
                             @Nullable String preimageInHex) {

        this.swapUuid = swapUuid;
        this.invoice = invoice;
        this.receiver = receiver;
        this.fundingOutput = fundingOutput;
        this.sweepFeeInSatoshis = sweepFeeInSatoshis;
        this.lightningFeeInSatoshis = lightningFeeInSatoshis;
        this.expiresAt = expiresAt;
        this.payedAt = payedAt;
        this.preimageInHex = preimageInHex;
    }
}
