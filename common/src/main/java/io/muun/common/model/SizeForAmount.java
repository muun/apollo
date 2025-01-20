package io.muun.common.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;
import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SizeForAmount {

    public long amountInSatoshis;

    public int sizeInBytes;

    @NotNull // Except for HWs withdrawals
    public String outpoint;

    @NotNull
    public UtxoStatus status;

    public int deltaInWeightUnits;

    public String derivationPath;

    public Integer addressVersion;

    /**
     * Manual constructor.
     */
    public SizeForAmount(
            long amountInSatoshis,
            int sizeInBytes,
            String outpoint,
            UtxoStatus status,
            int deltaInWeightUnits,
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
     * Json constructor.
     */
    public SizeForAmount() {
    }

    /**
     * Migration to init outpoints for pre-existing sizeForAmounts. Will be properly initialized
     * after first NTS refresh (e.g first newOperation, incoming operation, or any operationUpdate).
     */
    public SizeForAmount initOutpoint() {
        if (outpoint == null) {
            outpoint = "uninitialized";
        }
        return this;
    }

    /**
     * Migration to init utxo status for pre-existing sizeForAmounts. Will be properly initialized
     * after first NTS refresh (e.g first newOperation, incoming operation, or any operationUpdate).
     * NOTE: we're choosing to init status as CONFIRMED as this field has existed for a while now
     * in the codebase, and only users with a REALLY old version (circa mid-2020) should have null
     * here.
     */
    public SizeForAmount initUtxoStatusForApollo() {
        if (status == null) {
            status = UtxoStatus.CONFIRMED;
        }
        return this;
    }


    @Override
    public String toString() {
        return "[SizeForAmount " + sizeInBytes + " for " + amountInSatoshis + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final SizeForAmount that = (SizeForAmount) o;

        return (amountInSatoshis == that.amountInSatoshis && sizeInBytes == that.sizeInBytes
                && status == that.status && Objects.equals(outpoint, that.outpoint));
    }

    /**
     * Return the transaction id associated to the given outpoint.
     * Recall that outpoints are of the form "txId:outputIndex".
     */
    public String getOutpointTxId() {
        return outpoint.substring(0, outpoint.indexOf(":"));
    }
}
