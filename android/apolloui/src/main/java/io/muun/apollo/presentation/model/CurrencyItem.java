package io.muun.apollo.presentation.model;

import io.muun.common.model.Currency;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.money.CurrencyUnit;

public class CurrencyItem implements Comparable<CurrencyItem> {
    public final CurrencyUnit currencyUnit;
    public final Currency currencyInfo;

    public CurrencyItem(CurrencyUnit currencyUnit) {
        this.currencyUnit = currencyUnit;
        this.currencyInfo = Currency.getInfo(currencyUnit.getCurrencyCode()).orElse(null);
    }

    @Override
    public int compareTo(@NonNull CurrencyItem other) {
        final String thisName = currencyInfo.getName();
        final String otherName = other.currencyInfo.getName();

        return thisName.compareTo(otherName);
    }

    /**
     * Utility method to create a list of CurrencyItem.
     */
    public static List<CurrencyItem> create(Collection<CurrencyUnit> currencyUnits, boolean sort) {
        final List<CurrencyItem> items = new ArrayList<>(currencyUnits.size());

        for (CurrencyUnit currencyUnit: currencyUnits) {

            final CurrencyItem currencyItem = new CurrencyItem(currencyUnit);

            // Avoid adding (and showing) unsupported currencies
            if (currencyItem.currencyInfo != null) {
                items.add(currencyItem);
            }
        }

        if (sort) {
            Collections.sort(items);
        }

        return items;
    }
}
