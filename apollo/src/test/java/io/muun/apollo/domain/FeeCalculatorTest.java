package io.muun.apollo.domain;

import io.muun.apollo.BaseTest;
import io.muun.apollo.domain.errors.AmountTooSmallError;
import io.muun.apollo.domain.errors.InsufficientFundsError;
import io.muun.apollo.domain.utils.FeeCalculator;
import io.muun.common.model.SizeForAmount;
import io.muun.common.utils.BitcoinUtils;

import org.junit.Test;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static org.assertj.core.api.Java6Assertions.assertThat;

public class FeeCalculatorTest extends BaseTest {

    private static final long FEE_RATE = 10;
    private static final long DUST = BitcoinUtils.DUST_IN_SATOSHIS;

    private static List<SizeForAmount> defaultSizeProgression = new LinkedList<SizeForAmount>() {{
        // Note: each entry is more than double the previous (for easy testing):
        add(new SizeForAmount(103456, 110));
        add(new SizeForAmount(20345678, 230));
        add(new SizeForAmount(303456789, 340));
        add(new SizeForAmount(703456789, 580));
    }};

    private static FeeCalculator defaultFeeCalculator = new FeeCalculator(
            FEE_RATE,
            defaultSizeProgression
    );

    private static FeeCalculator emptyFeeCalculator = new FeeCalculator(
            FEE_RATE,
            Collections.emptyList()
    );

    @Test(expected = AmountTooSmallError.class)
    public void failsWhenGivenZero() {
        emptyFeeCalculator.getFeeForAmount(0);
    }

    @Test(expected = AmountTooSmallError.class)
    public void failsWhenGivenDust() {
        emptyFeeCalculator.getFeeForAmount(DUST - 1);
    }

    @Test(expected = InsufficientFundsError.class)
    public void failsWhenBalanceIsZero() {
        emptyFeeCalculator.getFeeForAmount(10000);
    }

    @Test(expected = InsufficientFundsError.class)
    public void failsWhenAmountExceedsBalance() {
        final SizeForAmount lastSizeForAmount = defaultSizeProgression
                .get(defaultSizeProgression.size() - 1);

        defaultFeeCalculator.getFeeForAmount(lastSizeForAmount.amountInSatoshis * 2);
    }

    @Test(expected = InsufficientFundsError.class)
    public void failsWhenAddingFeeExceedsBalance() {
        final SizeForAmount lastSizeForAmount = defaultSizeProgression
                .get(defaultSizeProgression.size() - 1);

        defaultFeeCalculator.getFeeForAmount(lastSizeForAmount.amountInSatoshis - 1);
    }

    @Test
    public void calculatesFeeForExactlyDust() {
        final long feeForExactlyDust = defaultFeeCalculator.getFeeForAmount(DUST);

        assertThat(feeForExactlyDust)
                .isEqualTo(defaultSizeProgression.get(0).sizeInBytes * FEE_RATE);
    }

    @Test
    public void calculatesFeeWhenInsideRange() {
        for (SizeForAmount sizeForAmount: defaultSizeProgression) {
            final long halfAmount = sizeForAmount.amountInSatoshis / 2;
            final long halfAmountFee = defaultFeeCalculator.getFeeForAmount(halfAmount);

            assertThat(halfAmountFee).isEqualTo(sizeForAmount.sizeInBytes * FEE_RATE);
        }
    }

    @Test
    public void calculatesFeeWhenAddingFeeExceedsRange() {
        for (int i = 0; i < defaultSizeProgression.size() - 1; i++) {
            final SizeForAmount sizeForAmount = defaultSizeProgression.get(i);

            final long amountBarelyWithinRange = sizeForAmount.amountInSatoshis - 1;
            final long amountExceedingRange = sizeForAmount.amountInSatoshis + 1;

            final long fee1 = defaultFeeCalculator.getFeeForAmount(amountBarelyWithinRange);
            final long fee2 = defaultFeeCalculator.getFeeForAmount(amountExceedingRange);

            assertThat(fee1).isEqualTo(fee2);
        }
    }

    @Test
    public void spendableAmountWithNoFunds() {
        final long spendableAmount = emptyFeeCalculator
                .getMaxSpendableAmount();

        assertThat(spendableAmount).isEqualTo(0);
    }

    @Test
    public void spendableAmountCorrectForOnlyEntry() {
        final SizeForAmount onlyEntry = new SizeForAmount(12345, 400);
        final List<SizeForAmount> transactionSize = Collections.singletonList(onlyEntry);

        final FeeCalculator feeCalculator = new FeeCalculator(FEE_RATE, transactionSize);

        final long spendableAmount = feeCalculator.getMaxSpendableAmount();

        // Is this spendableAmount calculated as one would expect?
        assertThat(spendableAmount)
                .isEqualTo(onlyEntry.amountInSatoshis - FEE_RATE * onlyEntry.sizeInBytes);

        // Are the remaining funds exactly equal to the calculated fee?
        final long remainingFunds = onlyEntry.amountInSatoshis - spendableAmount;
        final long calculatedFee = feeCalculator.getFeeForAmount(spendableAmount);

        assertThat(calculatedFee).isEqualTo(remainingFunds);

        // Does the coin spent actually equal the total balance?
        final long totalSpent = spendableAmount + calculatedFee;

        assertThat(totalSpent).isEqualTo(onlyEntry.amountInSatoshis);
    }

    @Test
    public void spendableAmountCorrectWithFunds() {
        final SizeForAmount lastEntry = defaultSizeProgression
                .get(defaultSizeProgression.size() - 1);

        final long totalBalance = lastEntry.amountInSatoshis;

        final long spendableAmount = defaultFeeCalculator.getMaxSpendableAmount();
        final long expectedSpendableAmount = (
                totalBalance - FEE_RATE * lastEntry.sizeInBytes
        );

        assertThat(spendableAmount).isEqualTo(expectedSpendableAmount);

        final long expectedFee = totalBalance - spendableAmount;
        final long calculatedFee = defaultFeeCalculator.getFeeForAmount(spendableAmount);

        assertThat(calculatedFee).isEqualTo(expectedFee);
        assertThat(spendableAmount + calculatedFee).isEqualTo(totalBalance);
    }
}
