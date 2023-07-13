package io.muun.apollo.presentation.ui.select_currency;

import io.muun.apollo.data.preferences.UserRepository;
import io.muun.apollo.domain.action.CurrencyActions;
import io.muun.apollo.domain.analytics.AnalyticsEvent;
import io.muun.apollo.domain.selector.ExchangeRateSelector;
import io.muun.apollo.domain.selector.UserSelector;
import io.muun.apollo.presentation.ui.base.BasePresenter;
import io.muun.apollo.presentation.ui.base.di.PerActivity;
import io.muun.common.Optional;
import io.muun.common.model.Currency;
import io.muun.common.model.ExchangeRateProvider;

import android.os.Bundle;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.inject.Inject;
import javax.money.CurrencyUnit;
import javax.validation.constraints.NotNull;

@PerActivity
public class SelectCurrencyPresenter extends BasePresenter<SelectCurrencyView> {

    // Note: SAT will actually only be displayed when selecting an amount INPUT currency (aka not
    // when selecting the user's primary currency)
    private static final List<String> TOP_CURRENCY_CODES = Arrays.asList(
            "BTC",
            "SAT",
            "EUR",
            "USD"
    );

    private final ExchangeRateSelector exchangeRateSelector;
    private final CurrencyActions currencyActions;
    private final UserRepository userRepository;

    private final UserSelector userSel;

    private CurrencyUnit primaryCurrency;
    private Optional<CurrencyUnit> selectedCurrency;

    /**
     * Constructor.
     */
    @Inject
    public SelectCurrencyPresenter(ExchangeRateSelector exchangeRateSelector,
                                   CurrencyActions currencyActions,
                                   UserRepository userRepository,
                                   UserSelector userSel) {
        this.exchangeRateSelector = exchangeRateSelector;
        this.currencyActions = currencyActions;
        this.userRepository = userRepository;
        this.userSel = userSel;
    }

    @Override
    public void setUp(@NotNull Bundle arguments) {
        super.setUp(arguments);

        selectedCurrency = SelectCurrencyActivity.getSelectedCurrency(arguments);

        view.setBitcoinUnit(userRepository.getBitcoinUnit());

        final ExchangeRateProvider exchangeRateProvider = getExchangeRateProvider();
        primaryCurrency = userSel.get().getPrimaryCurrency(exchangeRateProvider);

        view.setCurrencies(
                getTopCurrencies(),
                new HashSet<>(exchangeRateProvider.getCurrencies()),
                selectedCurrency.orElse(primaryCurrency),
                userRepository.getBitcoinUnit()
        );
    }

    private ExchangeRateProvider getExchangeRateProvider() {
        final Bundle args = view.getArgumentsBundle();
        final long fixedRateWindowId = SelectCurrencyActivity.getFixedExchangeRateWindowId(args);
        return exchangeRateSelector.getProviderForWindow(fixedRateWindowId);
    }

    private Set<CurrencyUnit> getTopCurrencies() {
        // set handle duplicates for us :)
        final Set<CurrencyUnit> topCurrencies = new TreeSet<>(buildTopCurrenciesOrder());
        final Bundle bundle = view.getArgumentsBundle();

        // 1. Add Muun pre-determined top currencies
        for (String code : TOP_CURRENCY_CODES) {

            if ("SAT".equals(code) && SelectCurrencyActivity.applySatAsACurrencyHack(bundle)) {
                // If we are selecting an amount INPUT currency... Then its HACK TIME!
                topCurrencies.add(SelectCurrencyActivity.getFakeSatCurrencyUnit());
            } else {
                Currency.getUnit(code).ifPresent(topCurrencies::add);
            }
        }

        // 2. Add user relevant currencies based on location (network and locale)
        topCurrencies.addAll(currencyActions.getLocalCurrencies());

        // 3. Add user-selected primary currency and pre-selected or in-selection process currency
        topCurrencies.add(primaryCurrency);
        selectedCurrency.ifPresent(topCurrencies::add);

        return topCurrencies;
    }

    private Comparator<CurrencyUnit> buildTopCurrenciesOrder() {
        return (currencyUnit1, currencyUnit2) -> {
            int index1 = TOP_CURRENCY_CODES.indexOf(currencyUnit1.getCurrencyCode());
            int index2 = TOP_CURRENCY_CODES.indexOf(currencyUnit2.getCurrencyCode());

            // If currencies aren't among top currencies, make them go last
            if (index1 < 0) {
                index1 = 1000;
            }
            if (index2 < 0) {
                index2 = 1000;
            }
            return index1 - index2;
        };
    }

    @Nullable
    @Override
    protected AnalyticsEvent getEntryEvent() {
        return new AnalyticsEvent.S_CURRENCY_PICKER();
    }
}
