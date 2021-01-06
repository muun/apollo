package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class IncomingSwapHtlcJson {

    public String uuid;

    public long expirationHeight;

    public long fulfillmentFeeSubsidyInSats;

    public long lentInSats;

    @NotNull
    public String address;

    public long outputAmountInSatoshis;

    @NotNull
    public String htlcTxHex;

    @Nullable // Present only if the swap is fulfilled
    public String fulfillmentTxHex;

    @NotNull
    public String swapServerPublicKeyHex;

    /**
     * JSON constructor.
     */
    public IncomingSwapHtlcJson() {
    }

    /**
     * Houston constructor.
     */
    public IncomingSwapHtlcJson(final String uuid,
                                final long expirationHeight,
                                final long fulfillmentFeeSubsidyInSats,
                                final long lentInSats,
                                final String address,
                                final long outputAmountInSatoshis,
                                final String htlcTxHex,
                                @Nullable final String fulfillmentTxHex,
                                final String swapServerPublicKeyHex) {
        this.uuid = uuid;
        this.expirationHeight = expirationHeight;
        this.fulfillmentFeeSubsidyInSats = fulfillmentFeeSubsidyInSats;
        this.lentInSats = lentInSats;
        this.address = address;
        this.outputAmountInSatoshis = outputAmountInSatoshis;
        this.htlcTxHex = htlcTxHex;
        this.fulfillmentTxHex = fulfillmentTxHex;
        this.swapServerPublicKeyHex = swapServerPublicKeyHex;
    }
}
