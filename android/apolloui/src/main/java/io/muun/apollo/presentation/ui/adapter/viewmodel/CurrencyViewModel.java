package io.muun.apollo.presentation.ui.adapter.viewmodel;

import io.muun.apollo.domain.model.BitcoinUnit;
import io.muun.apollo.presentation.model.CurrencyItem;
import io.muun.apollo.presentation.ui.adapter.holder.ViewHolderFactory;

public class CurrencyViewModel implements ItemViewModel {

    public final CurrencyItem model;
    public final BitcoinUnit bitcoinUnit;
    public final boolean isSelected;

    /**
     * Constructor.
     */
    public CurrencyViewModel(CurrencyItem model,
                             BitcoinUnit bitcoinUnit,
                             boolean isSelected) {
        this.model = model;
        this.bitcoinUnit = bitcoinUnit;
        this.isSelected = isSelected;
    }

    @Override
    public int type(ViewHolderFactory typeFactory) {
        return typeFactory.getLayoutRes(model);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final CurrencyViewModel that = (CurrencyViewModel) o;

        return model.currencyUnit.equals(that.model.currencyUnit)
                && bitcoinUnit == that.bitcoinUnit;
    }

    @Override
    public int hashCode() {
        return model.currencyUnit.hashCode() * 31 + bitcoinUnit.hashCode();
    }
}
