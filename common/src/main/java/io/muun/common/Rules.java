package io.muun.common;

public class Rules {

    /** The size of the address derivation window watched in advance. */
    public static final int EXTERNAL_ADDRESSES_WATCH_WINDOW_SIZE = 15;

    /** The minimum length of a user password. */
    public static final int PASSWORD_MIN_LENGTH = 8;

    /** The minimum length for an operation description. */
    public static final int OP_DESCRIPTION_MIN_LENGTH = 1;

    /**
     * The confirmation target (in blocks) for fee options displayed in clients.
     */
    public static final int CONF_TARGET_FAST = 1;
    public static final int CONF_TARGET_MID = 43;
    public static final int CONF_TARGET_SLOW = 90;

    /**
     * Precision used to compare fee rates for equality.
     */
    private static final double FEE_RATE_PRECISION = 0.01d;

    /** Useful to keep track of conversions. **/
    public static final int VBYTE_TO_WEIGHT_UNIT_RATIO = 4;  // 1 vbyte == 4 WU

    /** The minimum fee rate for an operation, in satoshis per weight unit. */
    public static final double OP_MINIMUM_FEE_RATE = 1d / VBYTE_TO_WEIGHT_UNIT_RATIO;

    /** The maximum fee rate for an operation, in satoshis per weight unit. */
    public static final double OP_MAXIMUM_FEE_RATE = 999d / VBYTE_TO_WEIGHT_UNIT_RATIO;

    public static double toSatsPerVbyte(double feeRateInSatsPerWeight) {
        return feeRateInSatsPerWeight * VBYTE_TO_WEIGHT_UNIT_RATIO;
    }

    public static double toSatsPerWeight(double feeRateInSatsPerVbyte) {
        return feeRateInSatsPerVbyte / VBYTE_TO_WEIGHT_UNIT_RATIO;
    }

    public static boolean feeRateEquals(double a, double b) {
        return Math.abs(a - b) < FEE_RATE_PRECISION;
    }

}
