package io.muun.apollo.presentation.ui.select_currency;

import io.muun.apollo.domain.model.BitcoinUnit;
import io.muun.apollo.presentation.ui.base.BaseView;

import java.util.Set;
import javax.money.CurrencyUnit;

interface SelectCurrencyView extends BaseView {

    void setBitcoinUnit(BitcoinUnit bitcoinUnit);

    void setCurrencies(Set<CurrencyUnit> topCurrencies,
                       Set<CurrencyUnit> allCurrencies,
                       CurrencyUnit selectedCurrency,
                       BitcoinUnit bitcoinUnit);

}
