package io.muun.apollo.domain.utils;


import io.muun.apollo.domain.errors.AmountTooSmallError;
import io.muun.apollo.domain.errors.InsufficientFundsError;
import io.muun.common.model.SizeForAmount;
import io.muun.common.utils.BitcoinUtils;

import java.util.List;

import javax.validation.constraints.NotNull;

public class FeeCalculator {

    @NotNull
    private final long satoshisPerByte;

    @NotNull
    private final List<SizeForAmount> sizeProgression;

    /**
     * Create a FeeCalculator for a FeeWindow, given an array of Transaction size estimations.
     */
    public FeeCalculator(long satoshisPerByte, List<SizeForAmount> sizeProgression) {
        this.satoshisPerByte = satoshisPerByte;
        this.sizeProgression = sizeProgression;
    }

    /**
     * Calculate the maximum spendable amount (in satoshis) for a total balance, subtracting the
     * fee it would cost to actually send it.
     */
    public long getMaxSpendableAmount() {
        if (sizeProgression.size() == 0) {
            return 0;
        }

        long peak = 0;
        for (SizeForAmount sizeForAmount : sizeProgression) {

            final long fee = sizeForAmount.sizeInBytes * satoshisPerByte;
            final long candidatePeak = sizeForAmount.amountInSatoshis - fee;

            // A new candidatePeak may not be higher than the current peak. This happens because
            // there are utxos that are worth less than the fee that it would cost to spend them.
            // And this is regardless of the order of the utxos in the sizeProgression.
            if (peak < candidatePeak) {
                peak = candidatePeak;
            }
        }

        return peak;
    }

    /**
     * Calculate the required fee to send a given amount, using the FeeWindow and optional
     * transaction size estimations provided on construction.
     */
    public long getFeeForAmount(long amountInSatoshis) {
        if (amountInSatoshis < BitcoinUtils.DUST_IN_SATOSHIS) {
            throw new AmountTooSmallError();
        }

        for (SizeForAmount sizeForAmount: sizeProgression) {
            if (sizeForAmount.amountInSatoshis < amountInSatoshis) {
                // With this entry, we don't have enough funds in UTXOs to cover the operation
                // amount. Next!
                continue;
            }

            final long fee = satoshisPerByte * sizeForAmount.sizeInBytes;

            if (sizeForAmount.amountInSatoshis < amountInSatoshis + fee) {
                // With this entry, we can cover the original amount, but not the amount plus the
                // fee we're about to recommend. Next!
                continue;
            }

            return fee;
        }

        throw new InsufficientFundsError(); // oh noes!
    }
}
