package io.muun.apollo.presentation.ui.fragments.enter_recovery_code;

import io.muun.apollo.R;
import io.muun.apollo.domain.errors.UserFacingError;
import io.muun.apollo.presentation.ui.base.SingleFragment;
import io.muun.apollo.presentation.ui.settings.RecoveryCodeView;
import io.muun.apollo.presentation.ui.view.MuunButton;
import io.muun.apollo.presentation.ui.view.MuunHeader;
import io.muun.apollo.presentation.ui.view.MuunHeader.Navigation;
import io.muun.apollo.presentation.ui.view.MuunRecoveryCodeBox;

import android.view.View;
import butterknife.BindView;
import butterknife.OnClick;

public class EnterRecoveryCodeFragment extends SingleFragment<EnterRecoveryCodePresenter>
        implements RecoveryCodeView {

    @BindView(R.id.signup_forgot_password_recovery_code_box)
    MuunRecoveryCodeBox recoveryCodeBox;

    @BindView(R.id.signup_forgot_password_continue)
    MuunButton continueButton;

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
        super.initializeUi(view);

        final MuunHeader header = getParentActivity().getHeader();
        header.setNavigation(Navigation.BACK);
        header.showTitle(R.string.login_title);
        header.setElevated(true);

        recoveryCodeBox.setEditable(true);
        recoveryCodeBox.setOnEditedListener(this::onRecoveryCodeEdited);
        recoveryCodeBox.requestFocusOnFirstEditableSegment();
        recoveryCodeBox.setOnKeyboardNextListeners();
        recoveryCodeBox.setOnKeyboardDoneListener(() -> {
            if (continueButton.isEnabled()) {
                continueButton.callOnClick();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        onRecoveryCodeEdited(recoveryCodeBox.getSegmentInputsContent()); // re-trigger validation
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
        continueButton.setLoading(isLoading);
        recoveryCodeBox.setEditable(!isLoading);
    }

    @Override
    public void setRecoveryCodeError(UserFacingError error) {
        recoveryCodeBox.setError(error);
    }

    @Override
    public void setConfirmEnabled(boolean isEnabled) {
        continueButton.setEnabled(isEnabled);
    }

    public void onRecoveryCodeEdited(String recoveryCodeString) {
        presenter.onRecoveryCodeEdited(recoveryCodeString);
    }

    @OnClick(R.id.signup_forgot_password_continue)
    public void onContinueButtonClick() {
        presenter.submitRecoveryCode(recoveryCodeBox.getSegmentInputsContent());
    }
}
