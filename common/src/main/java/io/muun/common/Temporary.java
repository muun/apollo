package io.muun.common;

public class Temporary {

    /**
     * TODO: by the time custom fees are fully implemented, this method should no longer exist.
     */
    public static long feeDoubleToLong(double fractionalFeeAsDouble) {
        return (long) Math.ceil(fractionalFeeAsDouble);
    }

}
