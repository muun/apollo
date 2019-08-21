package io.muun.common.api;

import io.muun.common.Supports;
import io.muun.common.dates.MuunZonedDateTime;
import io.muun.common.utils.Deprecated;

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
    @Deprecated(atApolloVersion = Supports.PreOpenChannel.APOLLO,
            atFalconVersion = Supports.PreOpenChannel.FALCON)
    public Long sweepFeeInSatoshis;

    @NotNull
    @Deprecated(atApolloVersion = Supports.PreOpenChannel.APOLLO,
            atFalconVersion = Supports.PreOpenChannel.FALCON)
    public Long lightningFeeInSatoshis;

    @NotNull
    public SubmarineSwapFeesJson fees;

    @NotNull
    public MuunZonedDateTime expiresAt;

    @NotNull
    public Boolean willPreOpenChannel;

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
                             SubmarineSwapFeesJson fees,
                             MuunZonedDateTime expiresAt,
                             Boolean willPreOpenChannel,
                             @Nullable MuunZonedDateTime payedAt,
                             @Nullable String preimageInHex) {

        this.swapUuid = swapUuid;
        this.invoice = invoice;
        this.receiver = receiver;
        this.fundingOutput = fundingOutput;
        this.fees = fees;
        this.expiresAt = expiresAt;
        this.willPreOpenChannel = willPreOpenChannel;
        this.payedAt = payedAt;
        this.preimageInHex = preimageInHex;

        // Compatibility:
        this.lightningFeeInSatoshis = fees.lightningInSats
                + fees.channelOpenInSats
                + fees.channelCloseInSats;

        this.sweepFeeInSatoshis = fees.sweepInSats;
    }
}
