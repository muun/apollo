package io.muun.apollo.presentation.ui.bundler;

import android.os.Bundle;
import icepick.Bundler;

import javax.money.CurrencyUnit;
import javax.money.Monetary;

public class CurrencyUnitBundler implements Bundler<CurrencyUnit> {
    @Override
    public void put(String key, CurrencyUnit currencyUnit, Bundle bundle) {
        bundle.putString(key, currencyUnit.getCurrencyCode());
    }

    @Override
    public CurrencyUnit get(String key, Bundle bundle) {
        return Monetary.getCurrency(bundle.getString(key));
    }
}