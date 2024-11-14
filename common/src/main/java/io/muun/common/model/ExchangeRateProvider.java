package io.muun.common.model;

import io.muun.common.Optional;
import io.muun.common.api.ExchangeRateWindow;

import org.javamoney.moneta.convert.ExchangeRateBuilder;
import org.javamoney.moneta.spi.AbstractRateProvider;
import org.javamoney.moneta.spi.DefaultNumberValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.money.CurrencyUnit;
import javax.money.MonetaryAmount;
import javax.money.convert.ConversionQuery;
import javax.money.convert.ExchangeRate;
import javax.money.convert.ProviderContextBuilder;
import javax.money.convert.RateType;

public class ExchangeRateProvider extends AbstractRateProvider {

    private final Map<String, Double> rates;
    private final List<CurrencyUnit> units;
    @Nullable // Only nullable for Houston (e.g constructed without ExchangeRateWindow)
    public ExchangeRateWindow rateWindow;

    /**
     * Apollo constructor. Construct an ExchangeRateProvider for a give ExchangeRateWindow.
     */
    public ExchangeRateProvider(ExchangeRateWindow exchangeRateWindow) {
        this(exchangeRateWindow.rates);
        this.rateWindow = exchangeRateWindow;
    }

    /**
     * Houston constructor. Construct an ExchangeRateProvider given a mapping of rates by currency
     * code.
     */
    public ExchangeRateProvider(Map<String, Double> availableRates) {
        super(ProviderContextBuilder.of("ExchangeRateProvider", RateType.REALTIME).build());

        this.rates = new HashMap<>(availableRates.size());
        this.units = new ArrayList<>(availableRates.size());

        for (Map.Entry<String, Double> entry: availableRates.entrySet()) {
            final String currencyCode = entry.getKey();
            final Double rate = entry.getValue();

            final Optional<CurrencyUnit> maybeCurrency = Currency.getUnit(currencyCode);

            if (maybeCurrency.isPresent()) {
                rates.put(currencyCode, rate);
                units.add(maybeCurrency.get());
            }
        }
    }

    /**
     * Convert an amount to a target currency given it's code.
     */
    public MonetaryAmount convert(MonetaryAmount amount, String targetCurrencyCode) {
        return amount.with(getCurrencyConversion(targetCurrencyCode));
    }

    /**
     * Convert an amount to a target currency.
     */
    public MonetaryAmount convert(MonetaryAmount amount, CurrencyUnit targetCurrency) {
        return amount.with(getCurrencyConversion(targetCurrency));
    }

    @Override
    @Nullable
    public ExchangeRate getExchangeRate(ConversionQuery query) {

        final CurrencyUnit sourceCurrency = query.getBaseCurrency();
        final CurrencyUnit targetCurrency = query.getCurrency();

        final Double sourceRate = rates.get(sourceCurrency.getCurrencyCode());
        final Double targetRate = rates.get(targetCurrency.getCurrencyCode());

        if (sourceRate == null || targetRate == null) {
            return null;
        }

        final Double rate = targetRate / sourceRate;

        return new ExchangeRateBuilder(getContext().getProviderName(), RateType.REALTIME)
                .setBase(sourceCurrency)
                .setTerm(targetCurrency)
                .setFactor(DefaultNumberValue.of(rate))
                .build();
    }

    public List<CurrencyUnit> getCurrencies() {
        return units;
    }
}
