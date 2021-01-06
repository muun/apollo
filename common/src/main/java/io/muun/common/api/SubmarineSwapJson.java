package io.muun.common.api;

import io.muun.common.Supports;
import io.muun.common.dates.MuunZonedDateTime;
import io.muun.common.utils.Deprecated;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
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

    @Nullable // Null if the invoice didn't have an amount
    @Deprecated(atApolloVersion = Supports.PreOpenChannel.APOLLO,
            atFalconVersion = Supports.PreOpenChannel.FALCON)
    public Long sweepFeeInSatoshis;

    @Nullable // Null if the invoice didn't have an amount
    @Deprecated(atApolloVersion = Supports.PreOpenChannel.APOLLO,
            atFalconVersion = Supports.PreOpenChannel.FALCON)
    public Long lightningFeeInSatoshis;

    @Nullable // Null if the invoice didn't have an amount
    public SubmarineSwapFeesJson fees;

    @NotNull
    public MuunZonedDateTime expiresAt;

    @NotNull
    @Deprecated(atApolloVersion = 76)
    public Boolean willPreOpenChannel;

    @Nullable
    public MuunZonedDateTime payedAt;

    @Nullable
    public String preimageInHex;

    @Nullable // Present if the invoice didn't have an amount
    public List<SubmarineSwapBestRouteFeesJson> bestRouteFees;

    @Nullable // Present if the invoice didn't have an amount
    public SubmarineSwapFundingOutputPoliciesJson fundingOutputPolicies;

    /**
     * Json constructor.
     */
    public SubmarineSwapJson() {
    }

    /**
     * Apollo constructor.
     */
    public SubmarineSwapJson(String swapUuid,
                             String invoice,
                             SubmarineSwapReceiverJson receiver,
                             SubmarineSwapFundingOutputJson fundingOutput,
                             @Nullable SubmarineSwapFeesJson fees,
                             MuunZonedDateTime expiresAt,
                             @Nullable MuunZonedDateTime payedAt,
                             @Nullable String preimageInHex) {
        this(
                swapUuid,
                invoice,
                receiver,
                fundingOutput,
                fees,
                expiresAt,
                false,
                payedAt,
                preimageInHex,
                null,
                null
        );
    }

    /**
     * Manual constructor.
     */
    public SubmarineSwapJson(
            String swapUuid,
            String invoice,
            SubmarineSwapReceiverJson receiver,
            SubmarineSwapFundingOutputJson fundingOutput,
            @Nullable SubmarineSwapFeesJson fees,
            MuunZonedDateTime expiresAt,
            Boolean willPreOpenChannel,
            @Nullable MuunZonedDateTime payedAt,
            @Nullable String preimageInHex,
            @Nullable List<SubmarineSwapBestRouteFeesJson> bestRouteFees,
            @Nullable SubmarineSwapFundingOutputPoliciesJson fundingOutputPolicies) {

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
        if (fees != null) {
            this.lightningFeeInSatoshis = fees.lightningInSats
                    + fees.channelOpenInSats
                    + fees.channelCloseInSats;

            this.sweepFeeInSatoshis = fees.sweepInSats;
        }

        this.bestRouteFees = bestRouteFees;
        this.fundingOutputPolicies = fundingOutputPolicies;
    }
}
