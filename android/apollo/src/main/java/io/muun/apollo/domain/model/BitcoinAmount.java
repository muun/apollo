package io.muun.apollo.domain.model;

import javax.money.MonetaryAmount;
import javax.validation.constraints.NotNull;

public class BitcoinAmount {

    @NotNull
    public final Long inSatoshis;

    @NotNull
    public final MonetaryAmount inInputCurrency;

    @NotNull
    public final MonetaryAmount inPrimaryCurrency;

    /**
     * Constructor.
     */
    public BitcoinAmount(
            @NotNull Long inSatoshis,
            @NotNull MonetaryAmount inInputCurrency,
            @NotNull MonetaryAmount inPrimaryCurrency) {

        this.inSatoshis = inSatoshis;
        this.inInputCurrency = inInputCurrency;
        this.inPrimaryCurrency = inPrimaryCurrency;
    }

    /**
     * Return the sum of two BitcoinAmounts.
     */
    public BitcoinAmount add(BitcoinAmount other) {
        // TODO we should NOT be adding MonetaryAmounts, instead recalculating with the implied
        // exchange rate using only satoshis.

        if (other == null) {
            return this;
        }

        return new BitcoinAmount(
                inSatoshis + other.inSatoshis,
                inInputCurrency.add(other.inInputCurrency),
                inPrimaryCurrency.add(other.inPrimaryCurrency)
        );
    }

    public boolean isZero() {
        return inInputCurrency.isZero();
    }
}
