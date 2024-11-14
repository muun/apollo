package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SizeForAmountJson {

    @NotNull
    public Long amountInSatoshis;

    @NotNull
    public Long sizeInBytes;

    @NotNull
    public String outpoint;

    @NotNull
    public UtxoStatusJson status;

    @NotNull
    public Integer deltaInWeightUnits;

    @NotNull
    public String derivationPath;

    @NotNull
    public Integer addressVersion;

    /**
     * Json constructor.
     */
    public SizeForAmountJson() {
        //
    }

    /**
     * Houston constructor.
     */
    public SizeForAmountJson(
            Long amountInSatoshis,
            Long sizeInBytes,
            String outpoint,
            UtxoStatusJson status,
            Integer deltaInWeightUnits,
            String derivationPath,
            Integer addressVersion

    ) {

        this.amountInSatoshis = amountInSatoshis;
        this.sizeInBytes = sizeInBytes;
        this.outpoint = outpoint;
        this.status = status;
        this.deltaInWeightUnits = deltaInWeightUnits;
        this.derivationPath = derivationPath;
        this.addressVersion = addressVersion;
    }

    /**
     * Return the transaction id associated to the given outpoint.
     * Recall that outpoints are of the form "txId:outputIndex".
     */
    public String getOutpointTxId() {
        return outpoint.substring(0, outpoint.indexOf(":"));
    }
}
