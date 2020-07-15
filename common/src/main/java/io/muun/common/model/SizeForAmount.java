package io.muun.common.model;


public class SizeForAmount {

    public long amountInSatoshis;
    public int sizeInBytes;
    public String outpoint;

    /**
     * Manual constructor.
     */
    public SizeForAmount(long amountInSatoshis,
                         int sizeInBytes,
                         String transactionHash,
                         int index) {

        this.amountInSatoshis = amountInSatoshis;
        this.sizeInBytes = sizeInBytes;
        this.outpoint = transactionHash + ":" + index;
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
