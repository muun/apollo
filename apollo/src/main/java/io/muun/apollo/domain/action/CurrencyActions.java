package io.muun.apollo.domain.action;

import io.muun.apollo.data.os.TelephonyInfoProvider;
import io.muun.common.Optional;
import io.muun.common.model.Currency;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryException;


@Singleton
public class CurrencyActions {

    private final TelephonyInfoProvider telephonyInfoProvider;

    @Inject
    public CurrencyActions(TelephonyInfoProvider telephonyInfoProvider) {
        this.telephonyInfoProvider = telephonyInfoProvider;
    }

    private Optional<CurrencyUnit> getCurrencyForLocale(Locale locale) {

        final CurrencyUnit currency;
        try {
            currency = Monetary.getCurrency(locale);
        } catch (MonetaryException e) {
            return Optional.empty();
        }

        if (!Currency.getInfo(currency.getCurrencyCode()).isPresent()) {
            return Optional.empty();
        }

        return Optional.of(currency);
    }

    private Set<CurrencyUnit> getCurrenciesForCountryCode(String countryCode) {

        final Set<CurrencyUnit> currencies = new HashSet<>();

        final Locale[] availableLocales = Locale.getAvailableLocales();

        for (Locale locale : availableLocales) {

            if (locale.getCountry().equalsIgnoreCase(countryCode)) {

                final Optional<CurrencyUnit> currency = getCurrencyForLocale(locale);

                if (currency.isPresent() && !currencies.contains(currency.get())) {
                    currencies.add(currency.get());
                }
            }
        }

        if (currencies.isEmpty()) {
            throw new UnsupportedOperationException("No currencies for country code");
        }

        return currencies;
    }

    private Set<CurrencyUnit> getDefaultCurrencies() {

        final Set<CurrencyUnit> currencies = new HashSet<>();

        // default first to the system's locale
        final Locale locale = Locale.getDefault();
        final Optional<CurrencyUnit> systemCurrency = getCurrencyForLocale(locale);
        if (systemCurrency.isPresent()) {
            currencies.add(systemCurrency.get());
            return currencies;
        }

        // else, default to dollars
        final Optional<CurrencyUnit> dollarCurrency = getCurrencyForLocale(Locale.US);
        if (dollarCurrency.isPresent()) {
            currencies.add(dollarCurrency.get());
            return currencies;
        }

        // if failed, return an empty set
        return currencies;
    }

    /**
     * Get the set of possible currencies for the current user, based on her network and locale.
     */
    public Set<CurrencyUnit> getLocalCurrencies() {

        final Optional<String> region = telephonyInfoProvider.getRegion();

        if (region.isPresent()) {
            return getCurrenciesForCountryCode(region.get());
        } else {
            return getDefaultCurrencies();
        }
    }
}
