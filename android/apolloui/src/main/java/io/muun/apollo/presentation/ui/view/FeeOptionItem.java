package io.muun.apollo.presentation.ui.view;

import io.muun.apollo.R;
import io.muun.apollo.databinding.FeeOptionItemBinding;
import io.muun.apollo.domain.model.BitcoinAmount;
import io.muun.apollo.domain.model.BitcoinUnit;
import io.muun.apollo.presentation.ui.helper.BitcoinHelper;
import io.muun.apollo.presentation.ui.helper.MoneyExtensionsKt;
import io.muun.apollo.presentation.ui.helper.MoneyHelper;
import io.muun.apollo.presentation.ui.utils.ConfirmationTimeFormatter;
import io.muun.apollo.presentation.ui.utils.UiUtils;
import io.muun.common.utils.Preconditions;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.core.content.ContextCompat;
import icepick.State;
import kotlin.jvm.functions.Function3;

import javax.annotation.Nullable;
import javax.money.MonetaryAmount;

public class FeeOptionItem extends MuunView<FeeOptionItemBinding> {

    @State
    BitcoinUnit bitcoinUnit;

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
    public Function3<LayoutInflater, ViewGroup, Boolean, FeeOptionItemBinding> bindingInflater() {
        return FeeOptionItemBinding::inflate;
    }

    private String getTitlePrefix() {
        return getContext().getString(R.string.fee_option_item_title);
    }

    private int getTextPrimaryColor() {
        return ContextCompat.getColor(getContext(), R.color.text_primary_color);
    }

    private int getTextSecondaryColor() {
        return ContextCompat.getColor(getContext(), R.color.text_secondary_color);
    }

    private int getDisabledTintColor() {
        return ContextCompat.getColor(getContext(), R.color.disabled_color);
    }

    /**
     * Set estimation for maximum confirmation time, for this fee option.
     */
    public void setMaxTimeMs(long timeMs) {
        final CharSequence timeText = new ConfirmationTimeFormatter(getContext()).formatMs(timeMs);

        final CharSequence text = TextUtils.concat(
                getTitlePrefix(),
                " ",
                new RichText(timeText).setBold()
        );

        getBinding().feeOptionTitle.setText(text);
    }

    /**
     * Set fee rate, in satoshis per virtual byte, for this fee option.
     */
    public void setFeeRate(double feeRateInSatsPerVbyte) {
        getBinding().feeOptionFeeRate.setText(getContext().getString(
                R.string.fee_option_item_fee_rate,
                UiUtils.formatFeeRate(feeRateInSatsPerVbyte)
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
        getBinding().feeOptionMainValue.setText(
                BitcoinHelper.formatLongBitcoinAmount(feeInSat, bitcoinUnit, getLocale())
        );
    }

    /**
     * Set fee value, in secondary currency, for this fee option.
     */
    private void setFeeInSecondaryCurrency(MonetaryAmount feeAmount) {
        getBinding().feeOptionSecondaryValue.setText(TextUtils.concat(
                "(",
                MoneyHelper.formatLongMonetaryAmount(feeAmount, bitcoinUnit, getLocale()),
                ")"
        ));
    }

    @Override
    public void setOnClickListener(@Nullable OnClickListener clickListener) {
        getBinding().feeOptionItem.setOnClickListener(clickListener);
    }

    @Override
    public boolean callOnClick() {
        /*
         * This method goes hand in hand with @setOnClickListener. If we assign #onClickListener
         * to an inner view, we should redirect @callOnClick too. A similar case could be made for
         * #performOnClick.
         */
        return getBinding().feeOptionItem.callOnClick();
    }

    @Override
    public void setSelected(boolean selected) {
        getBinding().feeOptionItem.setSelected(selected);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        getBinding().feeOptionItem.setEnabled(enabled);

        final int primary = enabled ? getTextPrimaryColor() : getDisabledTintColor();
        final int secondary = enabled ? getTextSecondaryColor() : getDisabledTintColor();
        getBinding().feeOptionTitle.setTextColor(primary);
        getBinding().feeOptionMainValue.setTextColor(primary);
        getBinding().feeOptionFeeRate.setTextColor(secondary);
        getBinding().feeOptionSecondaryValue.setTextColor(secondary);
    }

    public void setBitcoinUnit(BitcoinUnit bitcoinUnit) {
        this.bitcoinUnit = bitcoinUnit;
    }
}
