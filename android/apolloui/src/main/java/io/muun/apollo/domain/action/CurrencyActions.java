package io.muun.apollo.domain.action;

import io.muun.apollo.data.afs.InternalMetricsProvider;
import io.muun.apollo.domain.errors.MissingCurrencyError;
import io.muun.apollo.domain.errors.MissingLocaleError;
import io.muun.common.Optional;
import io.muun.common.model.Currency;

import androidx.annotation.VisibleForTesting;
import org.hibernate.validator.constraints.NotEmpty;
import timber.log.Timber;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryException;
import javax.money.UnknownCurrencyException;


@Singleton
public class CurrencyActions {

    private final InternalMetricsProvider internalMetricsProvider;

    @Inject
    public CurrencyActions(InternalMetricsProvider internalMetricsProvider) {
        this.internalMetricsProvider = internalMetricsProvider;
    }

    @VisibleForTesting
    Optional<CurrencyUnit> getCurrencyForLocale(Locale locale) {

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

    @VisibleForTesting
    @NotEmpty
    Set<CurrencyUnit> getCurrenciesForCountryCode(String countryCode) {

        final Set<CurrencyUnit> currencies = new HashSet<>();

        final Locale[] availableLocales = Locale.getAvailableLocales();
        final List<Locale> regionLocales = new ArrayList<>();

        for (Locale locale : availableLocales) {

            if (locale.getCountry().equalsIgnoreCase(countryCode)) {

                regionLocales.add(locale);

                final Optional<CurrencyUnit> maybeCurrency = getCurrencyForLocale(locale);

                if (maybeCurrency.isPresent() && !currencies.contains(maybeCurrency.get())) {
                    final String currencyCode = maybeCurrency.get().getCurrencyCode();

                    // If we have a special default for this currency, override and use it
                    if (Currency.OVERRIDES.get(currencyCode) != null) {
                        currencies.add(Currency.OVERRIDES.get(currencyCode));
                    } else {
                        currencies.add(maybeCurrency.get());
                    }
                }
            }
        }

        if (currencies.isEmpty()) {

            if (regionLocales.isEmpty()) {  // Should never happen
                Timber.e(new MissingLocaleError(countryCode));

            } else {
                Timber.e(
                        new MissingCurrencyError(
                                new UnknownCurrencyException(regionLocales.get(0)),
                                regionLocales
                        )
                );
            }

            // SAFE because we should always have support for our default currency
            currencies.add(Currency.getUnit(Currency.DEFAULT.getCode()).get());
        }

        return currencies;
    }

    /**
     * Get default currencies, when we can't reliably detect a region to find its currency.
     */
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

        final Optional<String> region = internalMetricsProvider.getRegion();

        if (region.isPresent()) {
            return getCurrenciesForCountryCode(region.get());
        } else {
            return getDefaultCurrencies();
        }
    }
}
