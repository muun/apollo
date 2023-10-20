package io.muun.apollo.presentation.ui.view;

import io.muun.apollo.R;
import io.muun.apollo.domain.ApplicationLockManager;
import io.muun.apollo.domain.errors.LocaleNumberParsingError;
import io.muun.apollo.domain.model.BitcoinAmount;
import io.muun.apollo.domain.model.BitcoinUnit;
import io.muun.apollo.presentation.ui.helper.BitcoinHelper;
import io.muun.apollo.presentation.ui.helper.MoneyExtensionsKt;
import io.muun.apollo.presentation.ui.helper.MoneyHelper;
import io.muun.apollo.presentation.ui.utils.UiUtils;
import io.muun.common.utils.Preconditions;

import android.content.Context;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import butterknife.BindString;
import butterknife.BindView;
import icepick.State;
import timber.log.Timber;

import java.math.RoundingMode;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.money.MonetaryAmount;

import static io.muun.common.utils.Dates.HOUR_IN_SECONDS;
import static io.muun.common.utils.Dates.MINUTE_IN_SECONDS;

public class FeeManualInput extends MuunView {

    public interface OnChangeListener {

        /**
         * This method is called to notify you that the fee rate in this input has changed. The
         * param value holds the new fee rate.
         */
        void onChange(Double feeRateInSatsPerVbyte);
    }

    @BindView(R.id.fee_input)
    MuunEditText feeRateInput;

    @BindView(R.id.fee_estimated_time)
    TextView estimatedTime;

    @BindView(R.id.fee_main_value)
    TextView mainValue;

    @BindView(R.id.fee_secondary_value)
    TextView secondaryValue;

    @BindString(R.string.fee_option_item_title)
    String maxTimePrefix;

    // State:
    @State
    Double feeRateInSatsPerVbyte;

    @State
    BitcoinUnit bitcoinUnit;

    // -----------------------------

    @Inject
    ApplicationLockManager lockManager;

    // -----------------------------

    private OnChangeListener onChangeListener;

    private boolean isSkippingListeners;

    public FeeManualInput(Context context) {
        super(context);
    }

    public FeeManualInput(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FeeManualInput(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.manual_fee_input;
    }

    @Override
    protected void setUp(@NonNull Context context, @Nullable AttributeSet attrs) {
        super.setUp(context, attrs);
        if (getComponent() != null) {
            getComponent().inject(this);
        }

        feeRateInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!isSkippingListeners) {
                    updateFeeRate(parseNumber(s.toString()));
                }
            }
        });
    }

    /**
     * Focus on this FeeManualInput and show the soft keyboard.
     * Our own version of {@link View#requestFocus()} but renamed since that one is final and we
     * can't override it.
     */
    public void requestFocusInput() {
        if (!lockManager.isLockSet()) {
            UiUtils.focusInput(feeRateInput); // Don't show soft keyboard if lock screen's showing
        }
    }

    /**
     * Set input's fee rate, in satoshis per virtual byte.
     */
    public void setFeeRate(double feeRateInSatsPerVbyte) {
        isSkippingListeners = true;
        feeRateInput.setText(UiUtils.formatFeeRate(feeRateInSatsPerVbyte, RoundingMode.FLOOR));
        isSkippingListeners = false;

        updateFeeRate(feeRateInSatsPerVbyte);
    }

    private void updateFeeRate(Double feeRateInSatsPerVbyte) {
        this.feeRateInSatsPerVbyte = feeRateInSatsPerVbyte;
        notifyChange();
    }

    public Double getFeeRateInVBytes() {
        return feeRateInSatsPerVbyte;
    }

    /**
     * Reset widget to initial state.
     */
    public void resetVisibility() {
        estimatedTime.setVisibility(View.INVISIBLE);
        mainValue.setVisibility(View.INVISIBLE);
        secondaryValue.setVisibility(View.INVISIBLE);
    }

    /**
     * Set estimation for maximum confirmation time, for this fee option.
     */
    public void setMaxTimeMs(long timeInMillis) {
        // TODO abstract this logic (also in InvoiceExpirationCountdownTimer)

        final long timeInSeconds = timeInMillis / 1000;

        final long hours = timeInSeconds / HOUR_IN_SECONDS;
        final long minutes = (timeInSeconds % HOUR_IN_SECONDS) / MINUTE_IN_SECONDS;

        final String timeText;
        if (hours > 0) {
            timeText = getContext().getString(R.string.fee_option_item_hs, hours);

        } else {
            timeText = getContext().getString(R.string.fee_option_item_mins, minutes);

        }

        final CharSequence text = TextUtils.concat(
                maxTimePrefix,
                " ",
                new RichText(timeText).setBold()
        );

        estimatedTime.setText(text);
        estimatedTime.setVisibility(VISIBLE);
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

        mainValue.setVisibility(View.VISIBLE);
        secondaryValue.setVisibility(View.VISIBLE);

    }

    private void setFeeInBtc(long feeInSat) {
        mainValue.setText(
                BitcoinHelper.formatLongBitcoinAmount(feeInSat, bitcoinUnit, getLocale())
        );
    }

    /**
     * Set fee value, in secondary currency, for this fee option.
     */
    private void setFeeInSecondaryCurrency(MonetaryAmount feeAmount) {
        secondaryValue.setText(TextUtils.concat(
                "(",
                MoneyHelper.formatLongMonetaryAmount(feeAmount, bitcoinUnit, getLocale()),
                ")"
        ));
    }

    public void setOnChangeListener(OnChangeListener onChangeListener) {
        this.onChangeListener = onChangeListener;
    }

    private void notifyChange() {
        if (onChangeListener != null) {
            onChangeListener.onChange(feeRateInSatsPerVbyte);
        }
    }

    public void setBitcoinUnit(BitcoinUnit mode) {
        this.bitcoinUnit = mode;
    }

    @Nullable
    private Double parseNumber(String input) {
        final Locale locale = getResources().getConfiguration().locale;

        try {
            return NumberFormat.getInstance(locale).parse(input).doubleValue();
        } catch (ParseException e) {

            // Only log if it's effectively a parse error (empty str can't be parsed into a double)

            final char decimalSeparator = new DecimalFormatSymbols(locale).getDecimalSeparator();
            final boolean isDecimalSeparator = Character.toString(decimalSeparator).equals(input);

            if (!TextUtils.isEmpty(input) && !isDecimalSeparator) {
                Timber.e(new LocaleNumberParsingError(input, locale, e));
            }

            return null;
        }
    }
}
