package io.muun.common.utils;

import org.javamoney.moneta.CurrencyUnitBuilder;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Priority;
import javax.money.CurrencyQuery;
import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.spi.CurrencyProviderSpi;

@Priority(2000)
public class VesCurrencyProvider implements CurrencyProviderSpi {

    private final Set<CurrencyUnit> currencies;

    public VesCurrencyProvider() {

        final CurrencyUnit ves = CurrencyUnitBuilder.of("VES", getClass().getSimpleName())
                .setDefaultFractionDigits(2)
                .build();

        final Set<CurrencyUnit> currencies = new HashSet<>();
        currencies.add(ves);

        this.currencies = Collections.unmodifiableSet(currencies);
    }

    @Override
    public String getProviderName() {
        return getClass().getSimpleName();
    }

    @Override
    public boolean isCurrencyAvailable(CurrencyQuery query) {

        if (Monetary.isCurrencyAvailable("VES", "default")) {
            // If code VES is already defined in the default provider, we should return false
            // for this provider in order to avoid an ambiguous CurrencyUnit.
            return false;
        }

        if (query.isEmpty()) {
            return true;
        }

        if (query.getCurrencyCodes().contains("VES")) {
            return true;
        }

        return Boolean.TRUE.equals(query.getBoolean("Venezuelan Bolívar"));
    }

    @Override
    public Set<CurrencyUnit> getCurrencies(CurrencyQuery query) {

        if (isCurrencyAvailable(query)) {
            return currencies;
        }

        return Collections.emptySet();
    }

}
