package io.muun.apollo.presentation.ui.view;

import io.muun.apollo.R;
import io.muun.apollo.domain.model.BitcoinAmount;
import io.muun.apollo.domain.model.CurrencyDisplayMode;
import io.muun.apollo.presentation.ui.helper.BitcoinHelper;
import io.muun.apollo.presentation.ui.helper.MoneyExtensionsKt;
import io.muun.apollo.presentation.ui.helper.MoneyHelper;
import io.muun.apollo.presentation.ui.utils.ConfirmationTimeFormatter;
import io.muun.apollo.presentation.ui.utils.UiUtils;
import io.muun.common.Rules;
import io.muun.common.utils.Preconditions;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;
import butterknife.BindColor;
import butterknife.BindString;
import butterknife.BindView;
import icepick.State;

import javax.annotation.Nullable;
import javax.money.MonetaryAmount;

public class FeeOptionItem extends MuunView {

    @BindView(R.id.fee_option_item)
    View layout;

    @BindView(R.id.fee_option_title)
    TextView title;

    @BindView(R.id.fee_option_fee_rate)
    TextView feeRate;

    @BindView(R.id.fee_option_main_value)
    TextView mainValue;

    @BindView(R.id.fee_option_secondary_value)
    TextView secondaryValue;

    @BindString(R.string.fee_option_item_title)
    String titlePrefix;

    @BindColor(R.color.text_primary_color)
    int textPrimaryColor;

    @BindColor(R.color.text_secondary_color)
    int textSecondaryColor;

    @BindColor(R.color.disabled_color)
    int disabledTintColor;

    @State
    CurrencyDisplayMode currencyDisplayMode;

    public FeeOptionItem(Context context) {
        super(context);
    }

    public FeeOptionItem(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FeeOptionItem(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.fee_option_item;
    }

    /**
     * Set estimation for maximum confirmation time, for this fee option.
     */
    public void setMaxTimeMs(long timeMs) {
        final CharSequence timeText = new ConfirmationTimeFormatter(getContext()).formatMs(timeMs);

        final CharSequence text = TextUtils.concat(
                titlePrefix,
                " ",
                new RichText(timeText).setBold()
        );

        title.setText(text);
    }

    /**
     * Set fee rate, for this fee option.
     */
    public void setFeeRate(double satoshisPerWeightUnit) {
        feeRate.setText(getContext().getString(
                R.string.fee_option_item_fee_rate,
                UiUtils.formatFeeRate(Rules.toSatsPerVbyte(satoshisPerWeightUnit))
        ));
    }

    /**
     * Set nominal fee value, for this fee option.
     */
    public void setFee(BitcoinAmount fee) {
        Preconditions.checkNotNull(fee); // Shouldn't reach here without this
        setFeeInBtc(fee.inSatoshis);

        // Don't show fee in btc twice! If input currency is btc, show fee in primary currency
        if (MoneyExtensionsKt.isBtc(fee.inInputCurrency)) {
            setFeeInSecondaryCurrency(fee.inPrimaryCurrency);

        } else {
            setFeeInSecondaryCurrency(fee.inInputCurrency);
        }

    }

    private void setFeeInBtc(long feeInSat) {
        mainValue.setText(
                BitcoinHelper.formatLongBitcoinAmount(feeInSat, currencyDisplayMode, getLocale())
        );
    }

    /**
     * Set fee value, in secondary currency, for this fee option.
     */
    private void setFeeInSecondaryCurrency(MonetaryAmount feeAmount) {
        secondaryValue.setText(TextUtils.concat(
                "(",
                MoneyHelper.formatLongMonetaryAmount(feeAmount, currencyDisplayMode, getLocale()),
                ")"
        ));
    }

    @Override
    public void setOnClickListener(@Nullable OnClickListener clickListener) {
        layout.setOnClickListener(clickListener);
    }

    @Override
    public boolean callOnClick() {
        /*
         * This method goes hand in hand with @setOnClickListener. If we assign #onClickListener
         * to an inner view, we should redirect @callOnClick too. A similar case could be made for
         * #performOnClick.
         */
        return layout.callOnClick();
    }

    @Override
    public void setSelected(boolean selected) {
        layout.setSelected(selected);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        layout.setEnabled(enabled);

        title.setTextColor(enabled ? textPrimaryColor : disabledTintColor);
        mainValue.setTextColor(enabled ? textPrimaryColor : disabledTintColor);
        feeRate.setTextColor(enabled ? textSecondaryColor : disabledTintColor);
        secondaryValue.setTextColor(enabled ? textSecondaryColor : disabledTintColor);
    }

    public void setCurrencyDisplayMode(CurrencyDisplayMode currencyDisplayMode) {
        this.currencyDisplayMode = currencyDisplayMode;
    }
}
