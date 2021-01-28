package io.muun.common.utils;

import org.javamoney.moneta.Money;
import org.junit.Test;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;

import static io.muun.common.utils.BitcoinUtils.bitcoinsToSatoshis;
import static io.muun.common.utils.BitcoinUtils.satoshisToBitcoins;
import static org.assertj.core.api.Assertions.assertThat;

public class BitcoinUtilsTest {

    @Test
    public void testBitcoinsToSatoshis() {
        final CurrencyUnit currency = Monetary.getCurrency("BTC");
        final MonetaryAmount amount = Money.of(1, currency);

        assertThat(bitcoinsToSatoshis(amount)).isEqualTo(100000000);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBitcoinsToSatoshisInvalidCurrency() {
        final CurrencyUnit currency = Monetary.getCurrency("USD");
        final MonetaryAmount amount = Money.of(1, currency);

        bitcoinsToSatoshis(amount);
    }

    @Test
    public void testSatoshisToBitcoins() {
        final CurrencyUnit currency = Monetary.getCurrency("BTC");
        final MonetaryAmount amount = Money.of(1, currency);

        assertThat(satoshisToBitcoins(100000000)).isEqualTo(amount);
    }
}
