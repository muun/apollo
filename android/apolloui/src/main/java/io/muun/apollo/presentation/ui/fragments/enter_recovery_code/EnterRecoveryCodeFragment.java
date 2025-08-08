package io.muun.apollo.presentation.ui.fragments.enter_recovery_code;

import io.muun.apollo.R;
import io.muun.apollo.databinding.SignupForgotPasswordFragmentBinding;
import io.muun.apollo.domain.errors.UserFacingError;
import io.muun.apollo.presentation.ui.base.SingleFragment;
import io.muun.apollo.presentation.ui.settings.RecoveryCodeView;
import io.muun.apollo.presentation.ui.view.MuunHeader;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.viewbinding.ViewBinding;
import kotlin.jvm.functions.Function3;

public class EnterRecoveryCodeFragment extends SingleFragment<EnterRecoveryCodePresenter>
        implements RecoveryCodeView {

    private SignupForgotPasswordFragmentBinding binding() {
        return (SignupForgotPasswordFragmentBinding) getBinding();
    }

    @Override
    protected Function3<LayoutInflater, ViewGroup, Boolean, ViewBinding> bindingInflater() {
        return SignupForgotPasswordFragmentBinding::inflate;
    }

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.signup_forgot_password_fragment;
    }

    @Override
    protected void initializeUi(View view) {
        final var binding = binding();
        final var recoveryCodeBox = binding.signupForgotPasswordRecoveryCodeBox;
        final var continueButton = binding.signupForgotPasswordContinue;

        recoveryCodeBox.setEditable(true);
        recoveryCodeBox.setOnEditedListener(presenter::onRecoveryCodeEdited);
        recoveryCodeBox.requestFocusOnFirstEditableSegment();
        recoveryCodeBox.setOnKeyboardNextListeners();
        recoveryCodeBox.setOnKeyboardDoneListener(() -> {
            if (continueButton.isEnabled()) {
                continueButton.callOnClick();
            }
        });

        continueButton.setOnClickListener(v ->
                presenter.submitRecoveryCode(recoveryCodeBox.getSegmentInputsContent())
        );
    }

    @Override
    protected void setUpHeader() {
        final MuunHeader header = getParentActivity().getHeader();
        header.setNavigation(MuunHeader.Navigation.BACK);
        header.showTitle(R.string.login_title);
        header.setElevated(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        // re-trigger validation
        final var recoveryCodeBox = binding().signupForgotPasswordRecoveryCodeBox;
        presenter.onRecoveryCodeEdited(recoveryCodeBox.getSegmentInputsContent());
        recoveryCodeBox.requestFocusOnFirstEditableSegment();
    }

    @Override
    protected boolean blockScreenshots() {
        return true;
    }

    @Override
    public boolean onBackPressed() {
        presenter.goBack();
        return true;
    }

    @Override
    public void setLoading(boolean isLoading) {
        final var binding = binding();
        binding.signupForgotPasswordContinue.setLoading(isLoading);
        binding.signupForgotPasswordRecoveryCodeBox.setEditable(!isLoading);
    }

    @Override
    public void setRecoveryCodeError(UserFacingError error) {
        binding().signupForgotPasswordRecoveryCodeBox.setError(error);
    }

    @Override
    public void setConfirmEnabled(boolean isEnabled) {
        binding().signupForgotPasswordContinue.setEnabled(isEnabled);
    }
}
