package io.muun.common.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

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

    /**
     * Manual constructor.
     */
    public SizeForAmount(long amountInSatoshis,
                         int sizeInBytes,
                         String outpoint,
                         UtxoStatus status,
                         int deltaInWeightUnits) {

        this.amountInSatoshis = amountInSatoshis;
        this.sizeInBytes = sizeInBytes;
        this.outpoint = outpoint;
        this.status = status;
        this.deltaInWeightUnits = deltaInWeightUnits;
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

        return (amountInSatoshis == that.amountInSatoshis && sizeInBytes == that.sizeInBytes);
    }
}
