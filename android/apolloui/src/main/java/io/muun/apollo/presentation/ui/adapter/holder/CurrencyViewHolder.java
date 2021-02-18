package io.muun.apollo.presentation.ui.adapter.holder;

import io.muun.apollo.R;
import io.muun.apollo.presentation.ui.adapter.viewmodel.CurrencyViewModel;
import io.muun.apollo.presentation.ui.helper.MoneyHelper;
import io.muun.apollo.presentation.ui.utils.UiUtils;
import io.muun.common.model.Currency;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import butterknife.BindView;

public class CurrencyViewHolder extends BaseViewHolder<CurrencyViewModel> {

    @BindView(R.id.currency_item_logo)
    ImageView logo;

    @BindView(R.id.currency_item_label)
    TextView label;

    @BindView(R.id.currency_item_selected_icon)
    ImageView selectedIcon;

    public CurrencyViewHolder(View view) {
        super(view);
    }

    @Override
    public void bind(CurrencyViewModel viewModel) {
        final Currency currency = viewModel.model.currencyInfo;

        final String code = MoneyHelper.formatCurrency(currency.getCode(), viewModel.mode);
        final String name = MoneyHelper.formatCurrencyName(currency, viewModel.mode);

        if (currency.getFlag() != null) {
            label.setText(String.format("%s %s (%s)", currency.getFlag(), name, code));
            logo.setVisibility(View.GONE);

        } else {

            if (currency.getCode().equals(Currency.BTC.getCode())) {
                logo.setImageResource(R.drawable.btc_logo);


            } else {
                logo.setImageResource(R.drawable.default_flag);
            }

            label.setText(String.format(" %s (%s)", name, code));
            logo.setVisibility(View.VISIBLE);
        }

        selectedIcon.setVisibility(viewModel.isSelected ? View.VISIBLE : View.GONE);
        UiUtils.setTint(selectedIcon, R.color.blue);
    }
}
