package io.muun.common;

public class Rules {

    /**
     * The minimum length of a user password.
     */
    public static final int PASSWORD_MIN_LENGTH = 8;

    /**
     * The confirmation target (in blocks) for a zero-conf Submarine Swap.
     * This value should be < Swapper's limit for dangerous swaps.
     */
    public static final int CONF_TARGET_FOR_ZERO_CONF_SWAP = 9;


}
