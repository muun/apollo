package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class IncomingSwapFulfillmentDataJson {

    public String fulfillmentTxHex;

    public String muunSignatureHex;

    public String outputPath;

    public int outputVersion;


    /**
     * JSON constructor.
     */
    public IncomingSwapFulfillmentDataJson() {
    }

    /**
     * Houston constructor.
     */
    public IncomingSwapFulfillmentDataJson(final String fulfillmentTxHex,
                                           final String muunSignatureHex,
                                           final String outputPath,
                                           final int outputVersion) {
        this.fulfillmentTxHex = fulfillmentTxHex;
        this.muunSignatureHex = muunSignatureHex;
        this.outputPath = outputPath;
        this.outputVersion = outputVersion;
    }
}
