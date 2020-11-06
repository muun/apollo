package io.muun.common.utils;

import org.javamoney.moneta.CurrencyUnitBuilder;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Priority;
import javax.money.CurrencyQuery;
import javax.money.CurrencyUnit;
import javax.money.spi.CurrencyProviderSpi;

@Priority(2000)
public class BitcoinCurrencyProvider implements CurrencyProviderSpi {

    private final Set<CurrencyUnit> currencies;

    /**
     * Constructor.
     */
    public BitcoinCurrencyProvider() {

        final CurrencyUnit btc = CurrencyUnitBuilder.of("BTC", getClass().getSimpleName())
                .setDefaultFractionDigits(BitcoinUtils.BITCOIN_PRECISION)
                .build();

        final Set<CurrencyUnit> currencies = new HashSet<>();
        currencies.add(btc);

        this.currencies = Collections.unmodifiableSet(currencies);
    }

    @Override
    public String getProviderName() {
        return getClass().getSimpleName();
    }

    @Override
    public boolean isCurrencyAvailable(CurrencyQuery query) {

        if (query.isEmpty()) {
            return true;
        }

        if (query.getCurrencyCodes().contains("BTC")) {
            return true;
        }

        return Boolean.TRUE.equals(query.getBoolean("Bitcoin"));

    }

    @Override
    public Set<CurrencyUnit> getCurrencies(CurrencyQuery query) {

        if (isCurrencyAvailable(query)) {
            return currencies;
        }

        return Collections.emptySet();
    }
}
