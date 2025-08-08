package io.muun.apollo.presentation.ui.fragments.phone_number;

import io.muun.apollo.R;
import io.muun.apollo.data.afs.InternalMetricsProvider;
import io.muun.apollo.domain.errors.UserFacingError;
import io.muun.apollo.domain.model.CountryInfo;
import io.muun.apollo.presentation.ui.base.SingleFragment;
import io.muun.apollo.presentation.ui.new_operation.TitleAndDescriptionDrawer;
import io.muun.apollo.presentation.ui.view.HtmlTextView;
import io.muun.apollo.presentation.ui.view.MuunButton;
import io.muun.apollo.presentation.ui.view.MuunCountryInput;
import io.muun.apollo.presentation.ui.view.MuunPhoneInput;
import io.muun.apollo.presentation.ui.view.MuunTextInput;
import io.muun.apollo.presentation.ui.view.RichText;
import io.muun.common.Optional;

import android.text.TextUtils;
import android.view.View;
import butterknife.BindView;
import butterknife.OnClick;

import javax.inject.Inject;

public class PhoneNumberFragment extends SingleFragment<PhoneNumberPresenter>
        implements PhoneNumberView {

    @BindView(R.id.explanation)
    HtmlTextView explanation;

    @BindView(R.id.signup_phone_number_edit_local_number)
    MuunPhoneInput nationalNumber;

    @BindView(R.id.signup_phone_number_edit_country_prefix)
    MuunTextInput countryPrefix;

    @BindView(R.id.signup_phone_number_country_picker)
    MuunCountryInput countryPicker;

    @BindView(R.id.signup_continue)
    MuunButton continueButton;

    @Inject
    InternalMetricsProvider internalMetricsProvider;

    @Override
    protected int getLayoutResource() {
        return R.layout.signup_phone_number_fragment;
    }

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected void initializeUi(View view) {
        countryPicker.setOnChangeListener(this::onCountryPickerChange);

        countryPrefix.setHintEnabled(false);
        countryPrefix.setHint(getString(R.string.default_country_prefix_hint));

        countryPrefix.setOnChangeListener(this, this::onCountryPrefixChange);

        nationalNumber.setOnKeyboardNextListener(continueButton::callOnClick);

        final String userCountryCode = internalMetricsProvider.getRegion().orElse(null);
        countryPicker.setValue(CountryInfo.findByCode(userCountryCode).orElse(null));

        final String whyThisText = getString(R.string.signup_phone_number_why_this);

        final CharSequence content = TextUtils.concat(
                getString(R.string.signup_phone_number_explanation),
                ". ",
                new RichText(whyThisText).setLink(this::onWhyThisClick)
        );

        explanation.setText(content);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (TextUtils.isEmpty(countryPrefix.getText())) {
            countryPrefix.requestFocusInput();

        } else {
            nationalNumber.requestFocusInput();
        }
    }

    @Override
    public boolean onBackPressed() {
        finishActivity();
        return true;
    }

    @Override
    public void setLoading(boolean isLoading) {
        nationalNumber.setEnabled(!isLoading);
        countryPrefix.setEnabled(!isLoading);
        countryPicker.setEnabled(!isLoading);
        continueButton.setLoading(isLoading);
    }

    @Override
    public void setPhoneNumberError(UserFacingError error) {
        nationalNumber.setError(error);

        if (error != null) {
            nationalNumber.requestFocusInput();
        }
    }

    @OnClick(R.id.signup_continue)
    void onContinueButtonClick() {
        presenter.submitPhoneNumber(
                countryPrefix.getText().toString() + nationalNumber.getText().toString()
        );
    }

    private void onWhyThisClick() {
        final TitleAndDescriptionDrawer dialog = new TitleAndDescriptionDrawer();
        dialog.setTitle(R.string.signup_phone_number_why_this_title);
        dialog.setDescription(getString(R.string.signup_phone_number_why_this_description));
        dialog.show(getParentFragmentManager(), null);

        presenter.reportShowPhoneNumberInfo();
    }

    private void onCountryPickerChange(Optional<CountryInfo> item) {
        final CountryInfo countryInfo = item.orElse(null);
        continueButton.setEnabled(countryInfo != null);

        if (countryInfo != null) {
            countryPrefix.setTextSkipListener("+" + countryInfo.countryNumber);
            nationalNumber.setCountryCode(countryInfo.countryCode);
            nationalNumber.requestFocusInput();

        } else {
            countryPrefix.setTextSkipListener("");
            nationalNumber.setCountryCode(null);
            countryPrefix.requestFocusInput();
        }
    }

    private void onCountryPrefixChange(String prefix) {
        int countryNumber;

        try {
            countryNumber = Integer.parseInt(prefix.replaceAll("[^\\d]", ""));
        } catch (NumberFormatException ex) {
            countryNumber = 0;
        }

        final CountryInfo countryInfo = CountryInfo.findByNumber(countryNumber).orElse(null);
        continueButton.setEnabled(countryInfo != null);

        if (countryInfo != null) {
            countryPrefix.setTextSkipListener("+" + countryInfo.countryNumber);
            countryPrefix.setSelection(countryPrefix.getText().length());
        }

        countryPicker.setValueSkipListener(countryInfo);
        nationalNumber.setCountryCode(countryInfo != null ? countryInfo.countryCode : null);
    }
}
