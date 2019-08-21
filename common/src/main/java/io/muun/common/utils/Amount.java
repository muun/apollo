package io.muun.common.utils;

public class Amount {

    private final long millisatoshis;

    private static long MSAT_SAT = 1_000L;

    private static long MSAT_BTC = 100_000_000_000L;

    private Amount(long millisatoshis) {
        this.millisatoshis = millisatoshis;
    }

    public static Amount fromSatoshis(long satoshis) {
        return new Amount(satoshis * MSAT_SAT);
    }

    public static Amount fromBtc(double btc) {
        return new Amount((long)(btc * MSAT_BTC));
    }

    public static Amount fromMilliSatoshis(long millisatoshis) {
        return new Amount(millisatoshis);
    }

    public long toMillisatoshis() {
        return millisatoshis;
    }

    public long toSatoshis() {
        return millisatoshis / MSAT_SAT;
    }

    public double toBtc() {
        return ((double) millisatoshis) / MSAT_BTC;
    }

    public Amount add(Amount other) {
        return new Amount(millisatoshis + other.toMillisatoshis());
    }

    public Amount sub(Amount other) {
        return new Amount(millisatoshis - other.toMillisatoshis());
    }

    /**
     * Get lightning fee.
     */
    public Amount getFee(Amount baseFee, long proportionalFeeMillionths) {
        final Amount proportionalFee
                = new Amount(millisatoshis * proportionalFeeMillionths / 1_000_000);
        return proportionalFee.add(baseFee);
    }

    /**
     * Add lightning fee.
     */
    public Amount addFee(Amount baseFee, long proportionalFeeMillionths) {
        return add(getFee(baseFee, proportionalFeeMillionths));
    }

    public boolean greater(Amount other) {
        return millisatoshis > other.millisatoshis;
    }

    public boolean less(Amount other) {
        return millisatoshis < other.millisatoshis;
    }

    public boolean greaterOrEqual(Amount other) {
        return millisatoshis >= other.millisatoshis;
    }

    public boolean lessOrEqual(Amount other) {
        return millisatoshis <= other.millisatoshis;
    }

    /**
     * Equals.
     */
    public boolean equals(Object obj) {
        if (obj != null && getClass() == obj.getClass()) {
            final Amount otherAmount = (Amount) obj;
            return millisatoshis == otherAmount.millisatoshis;
        }
        return false;
    }

    /**
     * Max.
     */
    public Amount max(Amount other) {
        return Amount.fromMilliSatoshis(Math.max(millisatoshis, other.millisatoshis));
    }

    public Amount min(Amount other) {
        return Amount.fromMilliSatoshis(Math.min(millisatoshis, other.millisatoshis));
    }

    @Override
    public String toString() {
        return Long.toString(millisatoshis);
    }
}
