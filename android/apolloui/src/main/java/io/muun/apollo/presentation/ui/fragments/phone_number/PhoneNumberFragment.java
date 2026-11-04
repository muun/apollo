package io.muun.apollo.presentation.ui.fragments.phone_number;

import io.muun.apollo.R;
import io.muun.apollo.data.afs.InternalMetricsProvider;
import io.muun.apollo.databinding.SignupPhoneNumberFragmentBinding;
import io.muun.apollo.domain.errors.UserFacingError;
import io.muun.apollo.domain.model.CountryInfo;
import io.muun.apollo.presentation.ui.base.SingleFragment;
import io.muun.apollo.presentation.ui.new_operation.TitleAndDescriptionDrawer;
import io.muun.apollo.presentation.ui.view.RichText;
import io.muun.common.Optional;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.viewbinding.ViewBinding;
import kotlin.jvm.functions.Function3;

import javax.inject.Inject;

public class PhoneNumberFragment extends SingleFragment<PhoneNumberPresenter>
        implements PhoneNumberView {

    private SignupPhoneNumberFragmentBinding binding() {
        return (SignupPhoneNumberFragmentBinding) getBinding();
    }

    @Override
    protected Function3<LayoutInflater, ViewGroup, Boolean, ViewBinding> bindingInflater() {
        return SignupPhoneNumberFragmentBinding::inflate;
    }

    @Inject
    InternalMetricsProvider internalMetricsProvider;

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected void initializeUi(View view) {
        binding().signupPhoneNumberCountryPicker.setOnChangeListener(this::onCountryPickerChange);
        binding().signupContinue.setOnClickListener(v -> {
            presenter.submitPhoneNumber(
                    binding().signupPhoneNumberEditCountryPrefix.getText().toString()
                            + binding().signupPhoneNumberEditLocalNumber.getText().toString()
            );
        });

        binding().signupPhoneNumberEditCountryPrefix.setHintEnabled(false);
        binding().signupPhoneNumberEditCountryPrefix
                .setHint(getString(R.string.default_country_prefix_hint));

        binding().signupPhoneNumberEditCountryPrefix
                .setOnChangeListener(this, this::onCountryPrefixChange);

        binding().signupPhoneNumberEditLocalNumber.setOnKeyboardNextListener(
                binding().signupContinue::callOnClick
        );

        final String userCountryCode = internalMetricsProvider.getRegion().orElse(null);
        binding().signupPhoneNumberCountryPicker
                .setValue(CountryInfo.findByCode(userCountryCode).orElse(null));

        final String whyThisText = getString(R.string.signup_phone_number_why_this);

        final CharSequence content = TextUtils.concat(
                getString(R.string.signup_phone_number_explanation),
                ". ",
                new RichText(whyThisText).setLink(this::onWhyThisClick)
        );

        binding().explanation.setText(content);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (TextUtils.isEmpty(binding().signupPhoneNumberEditCountryPrefix.getText())) {
            binding().signupPhoneNumberEditCountryPrefix.requestFocusInput();
        } else {
            binding().signupPhoneNumberEditLocalNumber.requestFocusInput();
        }
    }

    @Override
    public boolean onBackPressed() {
        finishActivity();
        return true;
    }

    @Override
    public void setLoading(boolean isLoading) {
        binding().signupPhoneNumberEditLocalNumber.setEnabled(!isLoading);
        binding().signupPhoneNumberEditCountryPrefix.setEnabled(!isLoading);
        binding().signupPhoneNumberCountryPicker.setEnabled(!isLoading);
        binding().signupContinue.setLoading(isLoading);
    }

    @Override
    public void setPhoneNumberError(UserFacingError error) {
        binding().signupPhoneNumberEditLocalNumber.setError(error);

        if (error != null) {
            binding().signupPhoneNumberEditLocalNumber.requestFocusInput();
        }
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
        binding().signupContinue.setEnabled(countryInfo != null);

        if (countryInfo != null) {
            binding().signupPhoneNumberEditCountryPrefix
                    .setTextSkipListener("+" + countryInfo.countryNumber);
            binding().signupPhoneNumberEditLocalNumber.setCountryCode(countryInfo.countryCode);
            binding().signupPhoneNumberEditLocalNumber.requestFocusInput();
        } else {
            binding().signupPhoneNumberEditCountryPrefix.setTextSkipListener("");
            binding().signupPhoneNumberEditLocalNumber.setCountryCode(null);
            binding().signupPhoneNumberEditCountryPrefix.requestFocusInput();
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
        binding().signupContinue.setEnabled(countryInfo != null);

        if (countryInfo != null) {
            binding().signupPhoneNumberEditCountryPrefix
                    .setTextSkipListener("+" + countryInfo.countryNumber);
            binding().signupPhoneNumberEditCountryPrefix.setSelection(
                    binding().signupPhoneNumberEditCountryPrefix.getText().length()
            );
        }

        binding().signupPhoneNumberCountryPicker.setValueSkipListener(countryInfo);
        binding().signupPhoneNumberEditLocalNumber.setCountryCode(
                countryInfo != null ? countryInfo.countryCode : null);
    }
}
