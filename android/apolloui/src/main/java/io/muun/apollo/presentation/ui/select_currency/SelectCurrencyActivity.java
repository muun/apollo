package io.muun.apollo.presentation.ui.select_currency;

import io.muun.apollo.R;
import io.muun.apollo.domain.model.BitcoinUnit;
import io.muun.apollo.presentation.model.CurrencyItem;
import io.muun.apollo.presentation.ui.adapter.ItemAdapter;
import io.muun.apollo.presentation.ui.adapter.holder.ViewHolderFactory;
import io.muun.apollo.presentation.ui.adapter.viewmodel.CurrencyViewModel;
import io.muun.apollo.presentation.ui.adapter.viewmodel.ItemViewModel;
import io.muun.apollo.presentation.ui.adapter.viewmodel.SectionHeaderViewModel;
import io.muun.apollo.presentation.ui.base.BaseActivity;
import io.muun.apollo.presentation.ui.utils.UiUtils;
import io.muun.apollo.presentation.ui.view.MuunHeader;
import io.muun.apollo.presentation.ui.view.MuunHeader.Navigation;
import io.muun.common.Optional;
import io.muun.common.model.Currency;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import butterknife.BindColor;
import butterknife.BindString;
import butterknife.BindView;
import icepick.State;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import javax.money.CurrencyContext;
import javax.money.CurrencyUnit;
import javax.validation.constraints.NotNull;

public class SelectCurrencyActivity extends BaseActivity<SelectCurrencyPresenter>
        implements SelectCurrencyView {

    private static final String SELECTED_CURRENCY_RESULT = "selected_currency_result";
    private static final String SELECTED_CURRENCY = "SELECTED_CURRENCY";
    private static final String FIXED_EXCHANGE_RATE_WINDOW_ID = "FIXED_EXCHANGE_RATE_WINDOW_ID";
    private static final String HEADER_TITLE = "HEADER_TITLE";

    @BindView(R.id.select_currency_header)
    MuunHeader header;

    @BindView(R.id.select_currency_list)
    RecyclerView recyclerView;

    @BindColor(R.color.icon_color)
    int toolbarMenuItemColor;

    @BindString(R.string.select_currency_most_popular_section_title)
    String mostPopularSectionTitle;

    @BindString(R.string.select_currency_all_section_title)
    String allCurrenciesSectionTitle;

    @State
    BitcoinUnit bitcoinUnit;

    private ItemAdapter adapter;

    /**
     * Creates an intent to launch the select primary currency activity.
     */
    public static Intent getSelectPrimaryCurrencyActivityIntent(@NotNull Context context,
                                                                long fixedExchangeRateWindowId) {
        return new Intent(context, SelectCurrencyActivity.class)
                .putExtra(HEADER_TITLE, context.getString(R.string.select_primary_currency_title))
                .putExtra(FIXED_EXCHANGE_RATE_WINDOW_ID, fixedExchangeRateWindowId);
    }

    /**
     * Creates an intent to launch an activity to select a currency, with a pre-selected one.
     */
    public static Intent getSelectCurrencyActivityIntent(@NotNull Context context,
                                                         @NotNull String selectedCurrencyCode,
                                                         long fixedExchangeRateWindowId) {
        return new Intent(context, SelectCurrencyActivity.class)
                .putExtra(SELECTED_CURRENCY, selectedCurrencyCode)
                .putExtra(FIXED_EXCHANGE_RATE_WINDOW_ID, fixedExchangeRateWindowId);
    }

    /**
     * Get the pre-selected currency from Android's bundle.
     */
    public static Optional<CurrencyUnit> getSelectedCurrency(@NotNull Bundle bundle) {
        final String selectedCurrencyCode = bundle.getString(SELECTED_CURRENCY);
        if ("SAT".equals(selectedCurrencyCode)) {
            return Optional.of(getFakeSatCurrencyUnit());
        }
        return Currency.getUnit(selectedCurrencyCode);
    }

    /**
     * Get the fixed exchange rate window id for the current top-level flow, from Android's bundle.
     */
    public static long getFixedExchangeRateWindowId(@NotNull Bundle bundle) {
        return bundle.getLong(FIXED_EXCHANGE_RATE_WINDOW_ID);
    }

    private static Optional<String> getHeaderTitle(@NotNull Bundle bundle) {
        return Optional.ofNullable(bundle.getString(HEADER_TITLE));
    }

    /**
     * Extract a Result from this Activity from intent.
     */
    public static String getResult(Intent data) {
        return data.getStringExtra(SELECTED_CURRENCY_RESULT);
    }

    /**
     * Helper to decide whether to apply "sat as a currency hack" or not. This
     * should be true when starting this activity to choose a currency for an input amount
     * (receive or send) and false when starting this activity to choose a primary currency.
     */
    public static boolean applySatAsACurrencyHack(@NotNull Bundle bundle) {
        return getSelectedCurrency(bundle).isPresent();
    }

    public static CurrencyUnit getFakeSatCurrencyUnit() {
        return new CurrencyUnit() {
            @Override
            public String getCurrencyCode() {
                return "SAT";
            }

            @Override
            public int getNumericCode() {
                return -1; // Undefined
            }

            @Override
            public int getDefaultFractionDigits() {
                return 0;
            }

            @Override
            public CurrencyContext getContext() {
                return Currency.getUnit("BTC").get().getContext();
            }

            @Override
            public int compareTo(CurrencyUnit o) {
                return -o.compareTo(this);
            }
        };
    }

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.select_currency_activity;
    }

    @Override
    protected void initializeUi() {
        super.initializeUi();

        header.attachToActivity(this);
        header.showTitle(R.string.select_currency_title);
        header.setNavigation(Navigation.BACK);

        getHeaderTitle(getArgumentsBundle()).ifPresent(header::showTitle);

        adapter = new ItemAdapter(new ViewHolderFactory());
        adapter.setOnItemClickListener(this::onItemClick);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        final MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.select_currency_activity, menu);

        final MenuItem searchItem = menu.findItem(R.id.search);
        UiUtils.setTintColor(searchItem.getIcon(), toolbarMenuItemColor);

        final SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setQueryHint(getString(R.string.search_currency_hint));
        searchView.setMaxWidth(Integer.MAX_VALUE);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {

            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (!TextUtils.isEmpty(newText) && newText.length() > 2) {
                    adapter.filter(SelectCurrencyActivity::filter, newText);
                } else {
                    adapter.resetFilter();
                }
                return false;
            }
        });

        searchView.setOnCloseListener(() -> {
            adapter.resetFilter();
            return false;
        });

        return true;
    }

    @Override
    public void setBitcoinUnit(BitcoinUnit bitcoinUnit) {
        this.bitcoinUnit = bitcoinUnit;
        if (applySatAsACurrencyHack(getArgumentsBundle())) {
            this.bitcoinUnit = BitcoinUnit.BTC; // If we'll always show SAT, let's show BTC as BTC
        }
    }

    private static boolean filter(ItemViewModel viewModel, String query) {
        if (viewModel instanceof CurrencyViewModel) {
            final CurrencyViewModel currencyViewModel = (CurrencyViewModel) viewModel;
            final Currency currency = currencyViewModel.model.currencyInfo;

            return currency.getCode().toLowerCase().contains(query.toLowerCase())
                    || currency.getName().toLowerCase().contains(query.toLowerCase());
        }
        return false;
    }

    @Override
    public void setCurrencies(Set<CurrencyUnit> topCurrencies,
                              Set<CurrencyUnit> allCurrencies,
                              CurrencyUnit selectedCurrency,
                              BitcoinUnit bitcoinUnit) {

        final List<ItemViewModel> topItems = toViewModel(topCurrencies, selectedCurrency, false);
        topItems.add(0, new SectionHeaderViewModel(mostPopularSectionTitle));

        adapter.clear();    // avoid nasty duplication after onResume
        adapter.addFirst(topItems);

        final List<ItemViewModel> sortedItems = toViewModel(allCurrencies, selectedCurrency, true);
        sortedItems.add(0, new SectionHeaderViewModel(allCurrenciesSectionTitle, true));

        adapter.addItems(sortedItems);
    }

    // TODO this could be made more generic
    private List<ItemViewModel> toViewModel(Collection<CurrencyUnit> currencies,
                                            CurrencyUnit selectedCurrency,
                                            boolean sort) {
        final List<CurrencyItem> currencyItems = CurrencyItem.create(currencies, sort);
        final ArrayList<ItemViewModel> viewModels = new ArrayList<>(currencies.size());

        for (CurrencyItem currency : currencyItems) {
            final String primaryCurrencyCode = selectedCurrency.getCurrencyCode();
            final boolean isSelected = currency.currencyInfo.getCode().equals(primaryCurrencyCode);

            viewModels.add(new CurrencyViewModel(currency, bitcoinUnit, isSelected));
        }

        return viewModels;
    }

    private void onItemClick(ItemViewModel viewModel) {
        if (viewModel instanceof CurrencyViewModel) {
            final CurrencyUnit currencyUnit = ((CurrencyViewModel) viewModel).model.currencyUnit;
            final Intent intent = new Intent();
            intent.putExtra(SELECTED_CURRENCY_RESULT, currencyUnit.getCurrencyCode());
            setResult(Activity.RESULT_OK, intent);
            finishActivity();
        }
    }
}
