package io.muun.apollo.presentation.ui.settings;

import io.muun.apollo.databinding.OldPasswordFragmentBinding;
import io.muun.apollo.domain.errors.UserFacingError;
import io.muun.apollo.presentation.ui.base.SingleFragment;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.viewbinding.ViewBinding;
import kotlin.jvm.functions.Function3;

public class OldPasswordFragment extends SingleFragment<OldPasswordPresenter>
        implements OldPasswordView {

    private OldPasswordFragmentBinding binding() {
        return (OldPasswordFragmentBinding) getBinding();
    }

    @Override
    protected Function3<LayoutInflater, ViewGroup, Boolean, ViewBinding> bindingInflater() {
        return OldPasswordFragmentBinding::inflate;
    }

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected void initializeUi(View view) {
        binding().enterOldPasswordInput.setPasswordRevealEnabled(true);
        binding().enterOldPasswordInput.setOnKeyboardNextListener(() -> {
            final String passwordText = binding().enterOldPasswordInput.getText().toString();
            if (shouldEnableContinueButton(passwordText)) {
                onContinueButtonClick();
            }
        });
        binding().enterOldPasswordInput.setOnChangeListener(
                this,
                this::conditionallyEnableContinueButton
        );

        conditionallyEnableContinueButton(binding().enterOldPasswordInput.getText().toString());

        binding().useRecoveryCode.setOnClickListener(v -> onUseRecoveryCodeButtonClick());
        binding().changePasswordContinue.setOnClickListener(v -> onContinueButtonClick());
    }

    @Override
    public void onResume() {
        super.onResume();
        binding().useRecoveryCode.setEnabled(true);

        setLoading(false);

        binding().enterOldPasswordInput.requestFocusInput();
    }

    @Override
    public void setLoading(boolean loading) {
        binding().changePasswordContinue.setLoading(loading);
        binding().enterOldPasswordInput.setEnabled(!loading);
        binding().useRecoveryCode.setEnabled(!loading);
    }

    @Override
    public void setPasswordError(UserFacingError error) {
        binding().enterOldPasswordInput.clearError();

        if (error != null) {
            binding().enterOldPasswordInput.setError(error);
            binding().enterOldPasswordInput.requestFocusInput();
        }
    }

    private void onUseRecoveryCodeButtonClick() {
        // avoid double tap while preparing next Fragment
        binding().useRecoveryCode.setEnabled(false);
        presenter.useRecoveryCode();
    }

    private void onContinueButtonClick() {
        setLoading(true); // avoid double tap while preparing next Fragment
        presenter.submitPassword(binding().enterOldPasswordInput.getText().toString());
    }

    private boolean shouldEnableContinueButton(String passwordText) {
        return !TextUtils.isEmpty(passwordText) && passwordText.length() >= 8;
    }

    private void conditionallyEnableContinueButton(String passwordText) {
        // We've seen some bizarre crashes that indicated fragment is not correctly initialized or
        // already being discarded when this event handler is called (e.g orientation change)
        if (getBinding() != null) {
            final boolean enable = shouldEnableContinueButton(passwordText);
            binding().changePasswordContinue.setEnabled(enable);
        }
    }
}
