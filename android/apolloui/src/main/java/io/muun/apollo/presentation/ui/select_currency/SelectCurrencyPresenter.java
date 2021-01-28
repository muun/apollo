package io.muun.apollo.presentation.ui.select_currency;

import io.muun.apollo.data.preferences.UserRepository;
import io.muun.apollo.domain.action.CurrencyActions;
import io.muun.apollo.domain.selector.ExchangeRateSelector;
import io.muun.apollo.domain.selector.UserSelector;
import io.muun.apollo.presentation.analytics.AnalyticsEvent;
import io.muun.apollo.presentation.ui.base.BasePresenter;
import io.muun.apollo.presentation.ui.base.di.PerActivity;
import io.muun.common.Optional;
import io.muun.common.model.Currency;
import io.muun.common.model.ExchangeRateProvider;

import android.app.Activity;
import android.os.Bundle;
import androidx.annotation.Nullable;
import rx.Observable;

import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import javax.money.CurrencyUnit;
import javax.validation.constraints.NotNull;

@PerActivity
public class SelectCurrencyPresenter extends BasePresenter<SelectCurrencyView> {

    private static final String[] TOP_CURRENCY_CODES = new String[]{"BTC", "EUR", "USD"};

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

        primaryCurrency = userSel.get().getPrimaryCurrency(exchangeRateSelector.getWindow());
        selectedCurrency = SelectCurrencyActivity.getSelectedCurrency(arguments);

        view.setCurrencyDisplayMode(userRepository.getCurrencyDisplayMode());

        setUpExchangeRates();
    }

    private void setUpExchangeRates() {
        final Observable<?> observable = exchangeRateSelector
                .watch()
                .compose(getAsyncExecutor())
                .doOnNext(this::onExchangeRatesChange);

        subscribeTo(observable);
    }

    private void onExchangeRatesChange(ExchangeRateProvider rates) {

        view.setCurrencies(
                getTopCurrencies(),
                new HashSet<>(rates.getCurrencies()),
                selectedCurrency.orElse(primaryCurrency),
                userRepository.getCurrencyDisplayMode()
        );
    }

    private Set<CurrencyUnit> getTopCurrencies() {
        final Set<CurrencyUnit> topCurrencies = new HashSet<>(); // set handle duplicates for us :)

        // 1. Add Muun pre-determined top currencies
        for (String topCurrenciesCode : TOP_CURRENCY_CODES) {
            Currency.getUnit(topCurrenciesCode).ifPresent(topCurrencies::add);
        }

        // 2. Add user relevant currencies based on location (network and locale)
        topCurrencies.addAll(currencyActions.getLocalCurrencies());

        // 3. Add user-selected primary currency and pre-selected or in-selection process currency
        topCurrencies.add(primaryCurrency);
        selectedCurrency.ifPresent(topCurrencies::add);

        return topCurrencies;
    }

    public void onCurrencyClick(CurrencyUnit currencyUnit) {
        view.finishWithResult(Activity.RESULT_OK, currencyUnit);
    }

    @Nullable
    @Override
    protected AnalyticsEvent getEntryEvent() {
        return new AnalyticsEvent.S_CURRENCY_PICKER();
    }
}
