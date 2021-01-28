package io.muun.apollo.presentation.ui.select_currency;

import io.muun.apollo.domain.model.CurrencyDisplayMode;
import io.muun.apollo.presentation.ui.base.BaseView;

import java.util.Set;
import javax.money.CurrencyUnit;

interface SelectCurrencyView extends BaseView {

    void setCurrencyDisplayMode(CurrencyDisplayMode mode);

    void setCurrencies(Set<CurrencyUnit> topCurrencies,
                       Set<CurrencyUnit> allCurrencies,
                       CurrencyUnit selectedCurrency,
                       CurrencyDisplayMode currencyDisplayMode);

    void finishWithResult(int resultCode, CurrencyUnit currencyUnit);

}
