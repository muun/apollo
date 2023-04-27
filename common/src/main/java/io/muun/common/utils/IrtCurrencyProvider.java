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
public class IrtCurrencyProvider implements CurrencyProviderSpi {

    private final Set<CurrencyUnit> currencies;

    /**
     * Constructor.
     */
    public IrtCurrencyProvider() {

        final CurrencyUnit irt = CurrencyUnitBuilder.of("IRT",getClass().getSimpleName())
                .setDefaultFractionDigits(2)
                .build();

        final Set<CurrencyUnit> currencies = new HashSet<>();
        currencies.add(irt);

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

        if (query.getCurrencyCodes().contains("IRT")) {
            return true;
        }

        return Boolean.TRUE.equals(query.getBoolean("Iranian Toman"));

    }

    @Override
    public Set<CurrencyUnit> getCurrencies(CurrencyQuery query) {

        if (isCurrencyAvailable(query)) {
            return currencies;
        }

        return Collections.emptySet();
    }

}
