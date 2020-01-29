package io.muun.common.utils;

import javax.annotation.Nonnegative;
import javax.annotation.Nullable;

public class MathUtils {

    /**
     * Like Math.pow(), but taking and returning long integers.
     */
    public static long longPow(long base, @Nonnegative int power) {
        if (power < 0) {
            // We just avoided an infinite loop. Whew! Good work, man.
            throw new IllegalArgumentException("longPow() takes only positive powers");
        }

        long result = 1;

        for (int i = 0; i < power; i++) {
            result *= base;
        }

        return result;
    }

    /**
     * The maximum between `a` and `b`, considering any number to be greater than null.
     */
    public static Integer maxWithNulls(@Nullable Integer a, @Nullable Integer b) {
        return a == null ? b : b == null ? a : (Integer) Math.max(a, b);
    }
}
