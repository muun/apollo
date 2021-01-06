package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class IncomingSwapJson {

    @NotNull
    public String uuid;

    @NotNull
    public String paymentHashHex;

    @Nullable // Missing if the swap is settled fully against debt
    public IncomingSwapHtlcJson htlc;

    @NotNull
    public String sphinxPacketHex;

    public long collectInSats;

    public long paymentAmountInSats;

    @Nullable // Only present once the swap is FULFILLED
    public String preimageHex;

    /**
     * Jackson constructor.
     */
    public IncomingSwapJson() {
    }

    /**
     * Houston constructor.
     */
    public IncomingSwapJson(final String uuid,
                            final String paymentHashHex,
                            @Nullable final IncomingSwapHtlcJson htlc,
                            final String sphinxPacketHex,
                            final long paymentAmountInSats,
                            final long collectInSats,
                            final String preimageHex) {
        this.uuid = uuid;
        this.paymentHashHex = paymentHashHex;
        this.htlc = htlc;
        this.sphinxPacketHex = sphinxPacketHex;
        this.paymentAmountInSats = paymentAmountInSats;
        this.collectInSats = collectInSats;
        this.preimageHex = preimageHex;
    }
}
