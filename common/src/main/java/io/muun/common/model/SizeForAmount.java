package io.muun.common.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SizeForAmount {

    public long amountInSatoshis;
    public int sizeInBytes;

    /**
     * Manual constructor.
     */
    public SizeForAmount(long amountInSatoshis, int sizeInBytes) {
        this.amountInSatoshis = amountInSatoshis;
        this.sizeInBytes = sizeInBytes;
    }

    /**
     * Json constructor.
     */
    public SizeForAmount() {
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
