package io.muun.apollo.presentation.ui.settings;

import io.muun.apollo.R;
import io.muun.apollo.domain.errors.UserFacingError;
import io.muun.apollo.presentation.ui.settings.edit_password.BaseEditPasswordFragment;
import io.muun.apollo.presentation.ui.view.MuunButton;
import io.muun.apollo.presentation.ui.view.MuunTextInput;

import android.text.TextUtils;
import android.view.View;
import butterknife.BindView;

public class OldPasswordFragment extends BaseEditPasswordFragment<OldPasswordPresenter>
        implements OldPasswordView {

    @BindView(R.id.enter_old_password_input)
    MuunTextInput password;

    @BindView(R.id.use_recovery_code)
    MuunButton useRecoveryCodeButton;

    @BindView(R.id.change_password_continue)
    MuunButton continueButton;

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.old_password_fragment;
    }

    @Override
    protected void initializeUi(View view) {
        super.initializeUi(view);

        password.setPasswordRevealEnabled(true);
        password.setOnKeyboardNextListener(() -> {
            final String passwordText = password.getText().toString();
            if (shouldEnableContinueButton(passwordText)) {
                onContinueButtonClick();
            }
        });
        password.setOnChangeListener(this, this::conditionallyEnableContinueButton);

        conditionallyEnableContinueButton(password.getText().toString());

        useRecoveryCodeButton.setOnClickListener(v -> onUseRecoveryCodeButtonClick());
        continueButton.setOnClickListener(v -> onContinueButtonClick());
    }

    @Override
    public void onResume() {
        super.onResume();
        useRecoveryCodeButton.setEnabled(true);

        setLoading(false);

        password.requestFocusInput();
    }

    @Override
    public void setLoading(boolean loading) {
        continueButton.setLoading(loading);
        password.setEnabled(!loading);
        useRecoveryCodeButton.setEnabled(!loading);
    }

    @Override
    public void setPasswordError(UserFacingError error) {
        password.clearError();

        if (error != null) {
            password.setError(error);
            password.requestFocusInput();
        }
    }

    private void onUseRecoveryCodeButtonClick() {
        useRecoveryCodeButton.setEnabled(false); // avoid double tap while preparing next Fragment
        presenter.useRecoveryCode();
    }

    private void onContinueButtonClick() {
        setLoading(true); // avoid double tap while preparing next Fragment
        presenter.submitPassword(password.getText().toString());
    }

    private boolean shouldEnableContinueButton(String passwordText) {
        return !TextUtils.isEmpty(passwordText) && passwordText.length() >= 8;
    }

    private void conditionallyEnableContinueButton(String passwordText) {
        // We've seen some bizarre crashes that indicated fragment is not correctly initialized or
        // already being discarded when this event handler is called (e.g orientation change)
        if (continueButton != null) {
            final boolean enable = shouldEnableContinueButton(passwordText);
            continueButton.setEnabled(enable);
        }
    }
}
