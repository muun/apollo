package io.muun.apollo.presentation.ui.fragments.enter_password;

import io.muun.apollo.R;
import io.muun.apollo.databinding.SignupUnlockFragmentBinding;
import io.muun.apollo.domain.errors.UserFacingError;
import io.muun.apollo.presentation.ui.activity.extension.MuunDialog;
import io.muun.apollo.presentation.ui.base.SingleFragment;
import io.muun.apollo.presentation.ui.view.MuunHeader;
import io.muun.apollo.presentation.ui.view.MuunHeader.Navigation;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.viewbinding.ViewBinding;
import kotlin.jvm.functions.Function3;

public class EnterPasswordFragment extends SingleFragment<EnterPasswordPresenter>
        implements EnterPasswordView {

    private SignupUnlockFragmentBinding binding() {
        return (SignupUnlockFragmentBinding) getBinding();
    }

    @Override
    protected Function3<LayoutInflater, ViewGroup, Boolean, ViewBinding> bindingInflater() {
        return SignupUnlockFragmentBinding::inflate;
    }

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.signup_unlock_fragment;
    }

    @Override
    protected void initializeUi(View view) {
        final var binding = binding();
        binding.signupUnlockEditPassword.setPasswordRevealEnabled(true);
        binding.signupUnlockEditPassword.setOnKeyboardNextListener(
                binding.signupContinue::callOnClick
        );
        binding.signupUnlockEditPassword.setOnChangeListener(this, (ignored) -> validateInput());
        bindOnClickListeners();
        validateInput();
    }

    @Override
    public void setUpHeader() {
        final MuunHeader header = getParentActivity().getHeader();
        header.setNavigation(Navigation.BACK);
        header.showTitle(R.string.login_title);
        header.setElevated(true);
    }

    private void validateInput() {
        final var binding = binding();
        binding.signupContinue.setEnabled(!presenter.isEmptyPassword(
                binding.signupUnlockEditPassword.getText().toString())
        );
    }

    @Override
    public void onResume() {
        super.onResume();
        binding().signupUnlockEditPassword.requestFocusInput();
    }

    @Override
    protected boolean blockScreenshots() {
        return true;
    }

    @Override
    public boolean onBackPressed() {

        final MuunDialog muunDialog = new MuunDialog.Builder()
                .title(R.string.signup_unlock_password_abort_title)
                .message(R.string.signup_unlock_password_abort_msg)
                .positiveButton(R.string.abort, 0, presenter::abortSignIn)
                .negativeButton(R.string.cancel, 0, null)
                .build();

        getParentActivity().showDialog(muunDialog);

        return true;
    }

    @Override
    public void setLoading(boolean isLoading) {
        final var binding = binding();
        binding.signupUnlockEditPassword.setEnabled(!isLoading);
        binding.signupContinue.setLoading(isLoading);
    }

    private void bindOnClickListeners() {
        final var binding = binding();
        binding.signupContinue.setOnClickListener(v ->
                presenter.submitPassword(
                        binding().signupUnlockEditPassword.getText().toString()
                )
        );
        binding.signupForgotPassword.setOnClickListener(v ->
                presenter.navigateToForgotPassword()
        );
    }

    @Override
    public void setPasswordError(UserFacingError error) {
        final var signupUnlockEditPassword = binding().signupUnlockEditPassword;
        signupUnlockEditPassword.setError(error);

        if (error != null) {
            signupUnlockEditPassword.requestFocusInput();
        }
    }

    @Override
    public void setReminderVisible(boolean isVisible) {
        setVisible(binding().signupUnlockReminder, isVisible);
    }

    @Override
    public void setForgotPasswordVisible(boolean isVisible) {
        setVisible(binding().signupForgotPassword, isVisible);
    }
}
