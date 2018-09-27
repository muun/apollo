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
}
