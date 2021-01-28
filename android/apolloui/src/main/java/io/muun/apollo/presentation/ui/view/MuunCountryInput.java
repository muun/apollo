package io.muun.apollo.presentation.ui.view;


import io.muun.apollo.R;
import io.muun.apollo.domain.model.CountryInfo;
import io.muun.apollo.presentation.ui.bundler.CountryInfoBundler;
import io.muun.apollo.presentation.ui.select_country.SelectCountryActivity;
import io.muun.apollo.presentation.ui.utils.UiUtils;
import io.muun.common.Optional;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.widget.TextView;
import butterknife.BindView;
import icepick.State;

import java.util.Objects;
import javax.annotation.Nullable;

public class MuunCountryInput extends MuunView {

    private static int REQUEST_COUNTRY = 4411;

    public interface OnChangeListener {
        void onChange(Optional<CountryInfo> countryInfo);
    }

    @BindView(R.id.selected_country)
    TextView selectedCountryView;

    @State(CountryInfoBundler.class)
    CountryInfo selectedCountry;

    private OnChangeListener onChangeListener;
    private boolean isSkippingListeners;

    public MuunCountryInput(Context context) {
        super(context);
    }

    public MuunCountryInput(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MuunCountryInput(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.muun_country_input;
    }

    @Override
    protected void setUp(Context context, @Nullable AttributeSet attrs) {
        super.setUp(context, attrs);

        final Drawable background = UiUtils.getTintedDrawable(
                getContext(),
                R.drawable.bg_muun_dropdown, // looks like a dropdown
                R.color.muun_spinner_drawable_color
        );

        selectedCountryView.setBackground(background);

        updateView();

        setClickable(true);
        setOnClickListener(v -> onClickSelf());
    }

    @Override
    protected void onRestoreInstanceState(Parcelable parcelable) {
        super.onRestoreInstanceState(parcelable);
        updateView();
    }

    public void setOnChangeListener(OnChangeListener onChangeListener) {
        this.onChangeListener = onChangeListener;
    }

    private void onClickSelf() {
        requestExternalResult(
                REQUEST_COUNTRY,
                SelectCountryActivity.getStartActivityIntent(getContext())
        );
    }

    @Override
    public void onExternalResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == REQUEST_COUNTRY) {
            if (resultCode == Activity.RESULT_OK) {
                setValue(SelectCountryActivity.getCountryFromResult(data));
            }
        } else {
            super.onExternalResult(requestCode, resultCode, data);
        }
    }

    public Optional<CountryInfo> getValue() {
        return Optional.ofNullable(selectedCountry);
    }

    /**
     * Set the value of this input.
     */
    public void setValue(@Nullable CountryInfo country) {
        if (Objects.equals(country, selectedCountry)) {
            return;
        }

        selectedCountry = country;
        updateView();
        notifyChange();
    }

    /**
     * Set the value of this input, without triggering listeners.
     */
    public void setValueSkipListener(@Nullable CountryInfo country) {
        isSkippingListeners =  true;
        setValue(country);
        isSkippingListeners = false;
    }

    private void updateView() {
        if (selectedCountry != null) {
            selectedCountryView.setText(selectedCountry.countryName);
        } else {
            selectedCountryView.setText(R.string.signup_phone_number_select_country);
        }
    }

    private void notifyChange() {
        if (onChangeListener != null && !isSkippingListeners) {
            onChangeListener.onChange(getValue());
        }
    }
}
