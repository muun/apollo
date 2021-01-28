package io.muun.apollo.presentation.ui.settings;

import io.muun.apollo.R;
import io.muun.apollo.domain.errors.UserFacingError;
import io.muun.apollo.presentation.ui.settings.edit_password.BaseEditPasswordFragment;
import io.muun.apollo.presentation.ui.view.MuunButton;
import io.muun.apollo.presentation.ui.view.MuunRecoveryCodeBox;

import android.view.View;
import butterknife.BindView;
import butterknife.OnClick;

public class RecoveryCodeFragment extends BaseEditPasswordFragment<RecoveryCodePresenter>
        implements RecoveryCodeView {

    @BindView(R.id.enter_recovery_code_box)
    MuunRecoveryCodeBox recoveryCodeBox;

    @BindView(R.id.enter_recovery_code_continue)
    MuunButton continueButton;

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.recovery_code_fragment;
    }

    @Override
    protected void initializeUi(View view) {
        super.initializeUi(view);

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

    @OnClick(R.id.use_password)
    public void onUsePasswordButtonlick() {
        getParentActivity().onBackPressed();
    }

    @OnClick(R.id.enter_recovery_code_continue)
    public void onContinueButtonClick() {
        presenter.submitRecoveryCode(recoveryCodeBox.getSegmentInputsContent());
    }

    @Override
    public void setRecoveryCodeError(UserFacingError error) {
        recoveryCodeBox.setError(error);
    }

    @Override
    public void setConfirmEnabled(boolean isEnabled) {
        continueButton.setEnabled(isEnabled);
    }

    private void onRecoveryCodeEdited(String recoveryCodeString) {
        presenter.onRecoveryCodeEdited(recoveryCodeString);
    }
}
