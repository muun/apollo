package io.muun.apollo.presentation.ui.view;

import io.muun.apollo.R;
import io.muun.apollo.domain.ApplicationLockManager;
import io.muun.apollo.domain.model.BitcoinUnit;
import io.muun.apollo.domain.utils.ExtensionsKt;
import io.muun.apollo.presentation.model.text_decoration.AutoSizeDecoration;
import io.muun.apollo.presentation.model.text_decoration.DecorationTransformation;
import io.muun.apollo.presentation.model.text_decoration.MoneyDecoration;
import io.muun.apollo.presentation.model.text_decoration.TextDecorator;
import io.muun.apollo.presentation.ui.helper.MoneyExtensionsKt;
import io.muun.apollo.presentation.ui.helper.MoneyHelper;
import io.muun.apollo.presentation.ui.select_currency.SelectCurrencyActivity;
import io.muun.apollo.presentation.ui.utils.UiUtils;
import io.muun.common.model.ExchangeRateProvider;
import io.muun.common.utils.BitcoinUtils;
import io.muun.common.utils.MoneyUtils;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import butterknife.BindColor;
import butterknife.BindView;
import butterknife.OnClick;
import icepick.State;
import org.javamoney.moneta.Money;

import java.math.BigDecimal;
import java.text.DecimalFormatSymbols;
import java.util.Objects;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.money.CurrencyUnit;
import javax.money.MonetaryAmount;
import javax.validation.constraints.NotNull;

import static android.app.Activity.RESULT_OK;


public class MuunAmountInput extends MuunView {

    public interface OnChangeListener {

        /**
         * This method is called to notify you that the amount in this input has changed. The
         * param values hold the old and the new amount.
         */
        void onChange(MonetaryAmount oldValue, MonetaryAmount newValue);
    }

    static final ViewProps<MuunAmountInput> viewProps
            = new ViewProps.Builder<MuunAmountInput>()
            .addFloat(R.attr.textMaxWidthPercent, MuunAmountInput::setTextMaxWidthPercent)
            .build();

    private static final int MIN_TEXT_SIZE_IN_DP = 24;
    private static final int REQUEST_CURRENCY = 1;

    // Components:
    @BindView(R.id.muun_amount)
    MuunEditText inputAmount;

    @BindView(R.id.secondary_amount)
    TextView secondaryAmount;

    @BindView(R.id.currency_code)
    TextView currencyInput;

    // Resources:
    @BindColor(R.color.blue)
    int normalNumberColor;

    @BindColor(R.color.text_secondary_color)
    int normalBalanceColor;

    @BindColor(R.color.error_color)
    int errorColor;

    // -----------------------------

    @Inject
    ApplicationLockManager lockManager;

    // State:
    @State
    float maxWidthPx;

    @State
    BitcoinUnit bitcoinUnit;

    private MonetaryAmount value;
    private MonetaryAmount valueBeforeCurrencyChange;

    private OnChangeListener onChangeListener;
    private AutoSizeDecoration autoSizeDecoration;
    private MoneyDecoration moneyDecoration;

    private ExchangeRateProvider rateProvider;

    private float textMaxWidthPercent;
    private DecimalFormatSymbols symbols;

    private boolean isMakingInternalChange;
    private boolean isCurrentlyChangingCurrency;

    public MuunAmountInput(Context context) {
        super(context);
    }

    public MuunAmountInput(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MuunAmountInput(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.view_amount_input;
    }

    @Override
    protected void setUp(@NonNull Context context, @Nullable AttributeSet attrs) {
        super.setUp(context, attrs);
        getComponent().inject(this);

        this.textMaxWidthPercent = 1f;
        viewProps.transfer(attrs, this);

        setUpNumberInput();
    }

    private void setUpNumberInput() {

        // Initial value needed to avoid NPEs in view initialization. User pref will be set and view
        // state updated correctly later.
        bitcoinUnit = BitcoinUnit.BTC;

        this.symbols = new DecimalFormatSymbols(getLocale());
        symbols.setCurrencySymbol("");

        this.autoSizeDecoration = new AutoSizeDecoration(
                inputAmount.getTextSize(),
                this.maxWidthPx,
                UiUtils.dpToPx(getContext(), MIN_TEXT_SIZE_IN_DP)
        );

        this.moneyDecoration = new MoneyDecoration(getLocale());

        new TextDecorator<>(inputAmount, new DecorationTransformation[]{
                moneyDecoration,
                autoSizeDecoration
        }).setAfterChangeListener(() -> onNumberInputChange(
                inputAmount.getText()
                        .toString()
                        .replace(MoneyDecoration.THIN_SPACE + "", "")
                        .replace(symbols.getDecimalSeparator(), '.')
        ));

        setValue(Money.of(0, "BTC"));
    }

    public MonetaryAmount getValue() {
        return value;
    }

    public boolean isEmpty() {
        return (value == null || !value.isPositive());
    }

    /**
     * Set the value of this input.
     */
    public void setValue(MonetaryAmount amount) {
        if (!MoneyUtils.equals(amount, value)) {
            this.valueBeforeCurrencyChange = amount;
            this.value = amount;

            adjustFractionalDigits();
            updateAmountText(false);
            updateCurrencyCodeText();
        }
    }

    /**
     * Set secondary amount's value and make it visible.
     */
    public void setSecondaryAmount(CharSequence amount) {
        this.secondaryAmount.setText(amount);
        this.secondaryAmount.setVisibility(View.VISIBLE);
    }


    /**
     * Make secondary amount disappear.
     */
    public void hideSecondaryAmount() {
        this.secondaryAmount.setVisibility(View.GONE);
    }

    /**
     * Set amount error state.
     */
    public void setAmountError(boolean hasError) {
        inputAmount.setTextColor(hasError ? errorColor : normalNumberColor);
    }

    public void setExchangeRateProvider(ExchangeRateProvider rateProvider) {
        this.rateProvider = rateProvider;
    }

    public void setOnChangeListener(OnChangeListener onChangeListener) {
        this.onChangeListener = onChangeListener;
    }

    /**
     * Set initial bitcoin unit.
     */
    public void setInitialBitcoinUnit(BitcoinUnit bitcoinUnit) {
        this.bitcoinUnit = bitcoinUnit;
        updateCurrencyCodeText(bitcoinUnit);
    }

    @OnClick(R.id.currency_code)
    protected void onCurrencyClicked() {
        if (rateProvider == null) {
            return; // If we're not completely initialized, briefly ignore input
        }

        final String currentCurrency = isSatSelectedAsCurrency()
                ? "SAT"
                : value.getCurrency().getCurrencyCode();

        requestExternalResult(
                REQUEST_CURRENCY,
                SelectCurrencyActivity.getSelectCurrencyActivityIntent(
                        getContext(),
                        currentCurrency,
                        Objects.requireNonNull(rateProvider.rateWindow).id
                )
        );
    }

    @Override
    public void onExternalResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CURRENCY) {
            if (resultCode == RESULT_OK) {
                onCurrencyInputChange(SelectCurrencyActivity.getResult(data));
            }

        } else {
            super.onExternalResult(requestCode, resultCode, data);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        this.maxWidthPx = ((View) this.getParent()).getMeasuredWidth() * textMaxWidthPercent;
        if (autoSizeDecoration != null) {
            autoSizeDecoration.setMaxWidthPx(maxWidthPx);
        }
    }

    private void onNumberInputChange(String numberString) {
        if (isMakingInternalChange) {
            return;
        }

        MonetaryAmount newValue = Money.of(parseNumber(numberString), value.getCurrency());

        if (MoneyExtensionsKt.isBtc(newValue) && getBitcoinUnit() == BitcoinUnit.SATS) {
            newValue = newValue.divide(BitcoinUtils.SATOSHIS_PER_BITCOIN);
        }

        final MonetaryAmount oldValue = value;
        value = newValue;

        // Once we start typing, text should have this color, unless overruled by setAmountError
        inputAmount.setTextColor(normalNumberColor);

        if (!isCurrentlyChangingCurrency) {
            valueBeforeCurrencyChange = newValue;
        }

        notifyChange(oldValue, newValue);
    }

    private void onCurrencyInputChange(String newCode) {
        if (isMakingInternalChange) {
            return;
        }

        if ("SAT".equals(newCode)) {
            bitcoinUnit = BitcoinUnit.SATS;
            newCode = "BTC";
        } else {
            bitcoinUnit = BitcoinUnit.BTC;
        }

        final MonetaryAmount newValue;

        if (rateProvider != null) {
            newValue = MoneyHelper.round(rateProvider.convert(valueBeforeCurrencyChange, newCode));
        } else {
            newValue = Money.of(value.getNumber(), newCode);
        }

        final MonetaryAmount oldValue = value;
        value = newValue;

        adjustFractionalDigits();
        updateCurrencyCodeText();
        updateAmountText(true);
        notifyChange(oldValue, newValue);
    }

    private void notifyChange(MonetaryAmount oldValue, MonetaryAmount newValue) {
        if (onChangeListener != null) {
            onChangeListener.onChange(oldValue, newValue);
        }
    }

    private void adjustFractionalDigits() {
        if (isSatSelectedAsCurrency()) {
            moneyDecoration.setMaxFractionalDigits(MoneyHelper.MAX_FRACTIONAL_DIGITS_SAT);
        } else if (value.getCurrency().getCurrencyCode().equals("BTC")) {
            moneyDecoration.setMaxFractionalDigits(MoneyHelper.MAX_FRACTIONAL_DIGITS_BTC);
        } else {
            moneyDecoration.setMaxFractionalDigits(MoneyHelper.MAX_FRACTIONAL_DIGITS_FIAT);
        }
    }

    private void setTextMaxWidthPercent(float textMaxWidthPercent) {
        this.textMaxWidthPercent = textMaxWidthPercent;
    }

    private void updateCurrencyCodeText() {
        updateCurrencyCodeText(getBitcoinUnit());
    }

    private void updateCurrencyCodeText(BitcoinUnit bitcoinUnit) {
        isMakingInternalChange = true;

        final CurrencyUnit currency = value.getCurrency();
        this.currencyInput.setText(MoneyHelper.formatCurrency(currency, bitcoinUnit));

        isMakingInternalChange = false;
    }

    private void updateAmountText(boolean isDueToCurrencyChange) {
        isMakingInternalChange = true;
        isCurrentlyChangingCurrency = isDueToCurrencyChange;

        if (value.isPositive()) {
            final String text = MoneyHelper.formatInputMonetaryAmount(
                    value,
                    getBitcoinUnit(),
                    ExtensionsKt.locale(getContext())
            );

            // Replace group separator with THIN_SPACE to play nice to MoneyDecoration and our
            // handleAndroidSupremeLocalizationBug. The idea is to avoid grouping separators to
            // behave "like the android textInput component" (which doesn't allow group separators).
            final char groupingSeparator = symbols.getGroupingSeparator();
            inputAmount.setText(text.replace(groupingSeparator, MoneyDecoration.THIN_SPACE));
            inputAmount.setSelection(inputAmount.getText().length());

        } else {
            inputAmount.setText("");
        }

        isCurrentlyChangingCurrency = false;
        isMakingInternalChange = false;
    }

    @NotNull
    private BigDecimal parseNumber(String numberString) {
        try {
            return new BigDecimal(numberString);

        } catch (NumberFormatException exception) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * Part of our (ugly) hack to allow SATs as an input currency option.
     */
    public BitcoinUnit getBitcoinUnit() {
        return bitcoinUnit;
    }

    /**
     * Part of our (ugly) hack to allow SATs as an input currency option.
     */
    public boolean isSatSelectedAsCurrency() {
        return MoneyExtensionsKt.isBtc(value) && bitcoinUnit == BitcoinUnit.SATS;
    }

    @Override
    public void setEnabled(boolean enabled) {
        inputAmount.setEnabled(enabled);
    }

    /**
     * Focus on this MuunAmountInput and show the soft keyboard.
     * Our own version of {@link View#requestFocus()} but renamed since that one is final and we
     * can't override it.
     */
    public void requestFocusInput() {
        if (!lockManager.isLockSet()) {
            UiUtils.focusInput(inputAmount); // Don't show soft keyboard if lock screen's showing
        }
    }
}
