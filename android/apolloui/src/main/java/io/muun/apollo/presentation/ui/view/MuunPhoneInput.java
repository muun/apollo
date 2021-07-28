package io.muun.apollo.presentation.ui.view;


import io.muun.apollo.R;
import io.muun.apollo.presentation.ui.utils.UiUtils;
import io.muun.common.model.PhoneNumber;

import android.content.Context;
import android.os.Parcelable;
import android.telephony.PhoneNumberFormattingTextWatcher;
import android.text.TextWatcher;
import android.util.AttributeSet;
import androidx.annotation.NonNull;
import icepick.State;

import javax.annotation.Nullable;

public class MuunPhoneInput extends MuunTextInput {

    @State
    protected String countryCode;

    private TextWatcher formatter;

    public MuunPhoneInput(Context context) {
        super(context);
    }

    public MuunPhoneInput(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MuunPhoneInput(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void setUp(@NonNull Context context, @Nullable AttributeSet attrs) {
        super.setUp(context, attrs);

        setHintEnabled(false);
        setCountryCode(null);
    }

    /**
     * Set a country code (eg "AR") to begin automatic formatting of user input, and set an example
     * phone number as hint.
     */
    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;

        setCountryHint(countryCode);
        setCountryFormatter(countryCode);
    }

    private void setCountryHint(String countryCode) {
        editText.setHint(
                PhoneNumber.getExample(countryCode)
                        .map(PhoneNumber::toNationalPrettyString)
                        .orElse(getDefaultCountryHint())
        );
    }

    @NonNull
    private String getDefaultCountryHint() {
        if (isInEditMode()) {
            return "";
        }

        return PhoneNumber.getExample(getContext().getString(R.string.default_country_code))
                .map(PhoneNumber::toNationalPrettyString)
                .orElse("");
    }

    private void setCountryFormatter(String countryCode) {

        if (isInEditMode()) {
            return;
        }

        editText.removeTextChangedListener(formatter);

        if (UiUtils.isLollipop()) {
            if (countryCode == null) {
                countryCode = getContext().getString(R.string.default_country_code);
            }
            formatter = new PhoneNumberFormattingTextWatcher(countryCode);

        } else {
            formatter = new PhoneNumberFormattingTextWatcher();
        }

        editText.addTextChangedListener(formatter);

        final String textWithoutFormat = editText.getText().toString().replaceAll("[^\\d]", "");

        setTextSkipListener(""); // force Watcher to restart formatting
        setText(textWithoutFormat);

        editText.setSelection(getText().length()); // move cursor to end
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Parcelable parcelable) {
        super.onRestoreInstanceState(parcelable);
        setCountryCode(countryCode);
    }
}
