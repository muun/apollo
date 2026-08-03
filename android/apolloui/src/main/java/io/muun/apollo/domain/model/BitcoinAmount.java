package io.muun.apollo.domain.model;

import io.muun.apollo.domain.libwallet.ExtensionsKt;

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

    public boolean isZero() {
        return inInputCurrency.isZero();
    }

    /**
     * Map from the libwallet representation of a BitcoinAmount.
     */
    public static BitcoinAmount fromLibwallet(newop.BitcoinAmount libwalletBtcAmount) {
        return new BitcoinAmount(
                libwalletBtcAmount.getInSat(),
                ExtensionsKt.adapt(libwalletBtcAmount.getInInputCurrency()),
                ExtensionsKt.adapt(libwalletBtcAmount.getInPrimaryCurrency())
        );
    }
}
