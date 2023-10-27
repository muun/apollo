package io.muun.common.model;

import org.assertj.core.util.Lists;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.List;

import static io.muun.common.model.BtcAmount.MSATS_PER_BTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.withPrecision;

/**
 * Checks that no unnecessary precision is lost when converting BTC amounts to {@link BtcAmount}.
 */
@RunWith(Parameterized.class)
public class BtcAmountTest {

    /** Number of decimal digits that can be represented in a double precision IEEE floating point
     *  number without losing any precision.
     * @see jdk.internal.math.FloatingDecimal#MAX_DECIMAL_DIGITS
     */
    private static final int DECIMAL_DIGITS_REPRESENTABLE_IN_DOUBLE = 15;
    private static final int DECIMAL_DIGITS = 10;
    private static final double ONE_MILLI_SATOSHI_IN_BTC = 1.0d / MSATS_PER_BTC;
    private final double btc;

    public BtcAmountTest(double btc) {
        this.btc = btc;
    }

    @Test
    public void fromAmountToBtcMinimumResolution() {
        assertThat(BtcAmount.fromBtc(btc).toBtc())
                .isEqualTo(
                        btc,
                        withPrecision(ONE_MILLI_SATOSHI_IN_BTC / 2)
                );
    }

    /**
     * Generates BTC amounts for all the possible quantities of <b>milliSatoshi</b>s of the form
     * d * 10^x + 1, i.e. a single decimal digit d followed of x zeros. Those amounts are then
     * perturbed by adding a single milliSatoshi. The exponent goes up to 15, which is the maximum
     * amount of decimal digits representable by a double precision IEEE number
     */
    @Parameters(name = "{index}: Amount {0,number,0.00000000000} btc")
    public static Iterable<Object> allOrdersOfMagnitude() {
        final List<Object> values = Lists.newArrayList();

        for (int i = 1; i < DECIMAL_DIGITS; i++) {
            values.add(multipleOfOneMilliSatoshi(0, i));
        }
        for (int position = 1; position < DECIMAL_DIGITS_REPRESENTABLE_IN_DOUBLE; position++) {
            for (int digit = 1; digit < DECIMAL_DIGITS; digit++) {
                values.add(multipleOfOneMilliSatoshi(position, digit) + ONE_MILLI_SATOSHI_IN_BTC);
            }
        }
        return values;
    }

    /**
     * Generates a BTC amount based on a quantity of <b>milliSatoshi</b>s of the form
     * <code>digitValue</code> * 10^<code>digitPosition</code> where <code>digitValue</code>
     * is a number in [0, 9].
     * @param digitPosition the position of the digit
     * @param digitValue the digit of the amount
     * @return The BTC amount built as specified
     */
    private static double multipleOfOneMilliSatoshi(int digitPosition, int digitValue) {
        return ONE_MILLI_SATOSHI_IN_BTC * Math.pow(DECIMAL_DIGITS, digitPosition) * digitValue;
    }
}