package io.muun.common.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import javax.validation.constraints.NotNull;

/**
 * Immutable Bitcoin amount.
 */
public class BtcAmount {

    public static final BtcAmount ZERO = new BtcAmount(0);

    private static final long MSATS_PER_SAT = 1_000L;

    private static final long MSATS_PER_BTC = 100_000_000_000L;

    private final long milliSats;

    private BtcAmount(long milliSats) {
        this.milliSats = milliSats;
    }

    public static BtcAmount fromSats(long satoshis) {
        return new BtcAmount(satoshis * MSATS_PER_SAT);
    }

    public static BtcAmount fromBtc(double btc) {
        return new BtcAmount((long)(btc * MSATS_PER_BTC));
    }

    public static BtcAmount fromMilliSats(long milliSatoshis) {
        return new BtcAmount(milliSatoshis);
    }

    public long toMilliSats() {
        return milliSats;
    }

    /**
     * Convert to Sats.
     *
     * @deprecated Use toSats(RoundingMode) for explicit rounding
     */
    @Deprecated
    public long toSats() {
        return milliSats / MSATS_PER_SAT;
    }

    public long toSats(final RoundingMode roundingMode) {
        return BigDecimal.valueOf(milliSats)
                .divide(BigDecimal.valueOf(MSATS_PER_SAT), roundingMode)
                .longValueExact();
    }

    public double toBtc() {
        return ((double) milliSats) / MSATS_PER_BTC;
    }

    public BtcAmount add(@NotNull BtcAmount other) {
        return new BtcAmount(milliSats + other.toMilliSats());
    }

    public BtcAmount sub(@NotNull BtcAmount other) {
        return new BtcAmount(milliSats - other.toMilliSats());
    }

    public boolean greaterThan(@NotNull BtcAmount other) {
        return milliSats > other.milliSats;
    }

    public boolean lessThan(@NotNull BtcAmount other) {
        return milliSats < other.milliSats;
    }

    public boolean greaterOrEqualThan(@NotNull BtcAmount other) {
        return milliSats >= other.milliSats;
    }

    public boolean lessOrEqualThan(@NotNull BtcAmount other) {
        return milliSats <= other.milliSats;
    }

    public boolean isZero() {
        return milliSats == 0;
    }

    public BtcAmount max(@NotNull BtcAmount other) {
        return BtcAmount.fromMilliSats(Math.max(milliSats, other.milliSats));
    }

    public BtcAmount min(@NotNull BtcAmount other) {
        return BtcAmount.fromMilliSats(Math.min(milliSats, other.milliSats));
    }

    /**
     * Compute the lightning fee for this amount, given specific base and proportional fee rates.
     */
    public BtcAmount computeFee(@NotNull BtcAmount baseFee, long proportionalFeeMillionths) {

        return new BtcAmount(baseFee.milliSats + milliSats * proportionalFeeMillionths / 1_000_000);
    }

    @Override
    public boolean equals(Object other) {

        if (other == null || getClass() != other.getClass()) {
            return false;
        }

        return milliSats == ((BtcAmount) other).milliSats;
    }

    @Override
    public String toString() {
        return milliSats + " msats";
    }
}
