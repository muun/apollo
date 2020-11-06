package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class IncomingSwapJson {

    @NotNull
    public String uuid;

    @NotNull
    public String paymentHashHex;

    @NotNull
    public IncomingSwapHtlcJson htlc;

    @NotNull
    public String sphinxPacketHex;

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
                            final IncomingSwapHtlcJson htlc,
                            final String sphinxPacketHex) {
        this.uuid = uuid;
        this.paymentHashHex = paymentHashHex;
        this.htlc = htlc;
        this.sphinxPacketHex = sphinxPacketHex;
    }
}
