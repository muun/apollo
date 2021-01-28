package io.muun.apollo.presentation.ui.recovery_code.verify;

import io.muun.apollo.R;
import io.muun.apollo.domain.errors.UserFacingError;
import io.muun.apollo.domain.libwallet.RecoveryCodeV2;
import io.muun.apollo.presentation.ui.base.SingleFragment;
import io.muun.apollo.presentation.ui.recovery_code.SetupRecoveryCodeActivity;
import io.muun.apollo.presentation.ui.view.MuunButton;
import io.muun.apollo.presentation.ui.view.MuunHeader;
import io.muun.apollo.presentation.ui.view.MuunRecoveryCodeBox;

import android.view.View;
import butterknife.BindView;

import java.util.List;

public class VerifyRecoveryCodeFragment extends SingleFragment<VerifyRecoveryCodePresenter>
        implements VerifyRecoveryCodeView {

    @BindView(R.id.recovery_code_box)
    MuunRecoveryCodeBox recoveryCodeBox;

    @BindView(R.id.accept)
    MuunButton acceptButton;

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.verify_recovery_code_fragment;
    }

    @Override
    protected void initializeUi(View view) {
        super.initializeUi(view);

        setUpHeader();
        recoveryCodeBox.setOnEditedListener(this::onRecoveryCodeEdited);
        acceptButton.setOnClickListener(this::onConfirmClick);
    }

    private void setUpHeader() {
        final String indicatorText = getString(
                R.string.set_up_rc_step_count,
                2,
                SetupRecoveryCodeActivity.SET_UP_RC_STEP_COUNT
        );

        final MuunHeader header = getParentActivity().getHeader();
        header.setIndicatorText(indicatorText);
        header.setElevated(true);
        header.setNavigation(MuunHeader.Navigation.BACK);
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
    public void setRecoveryCode(RecoveryCodeV2 recoveryCode) {
        recoveryCodeBox.setRecoveryCode(recoveryCode);
    }

    @Override
    public void setSegmentsToVerify(List<Integer> segmentsToVerify) {
        for (int i = 0; i < RecoveryCodeV2.SEGMENT_COUNT; i++) {
            final boolean wasEditable = recoveryCodeBox.isSegmentEditable(i);
            final boolean isEditable = segmentsToVerify.contains(i);

            recoveryCodeBox.setSegmentEditable(i, isEditable);

            if (isEditable && !wasEditable) {
                recoveryCodeBox.clearSegment(i);
            }
        }

        recoveryCodeBox.requestFocusOnFirstEditableSegment();
        recoveryCodeBox.setOnKeyboardNextListeners();
        recoveryCodeBox.setOnKeyboardDoneListener(() -> {
            if (acceptButton.isEnabled()) {
                acceptButton.callOnClick();
            }
        });
    }

    @Override
    public void setConfirmEnabled(boolean isEnabled) {
        acceptButton.setEnabled(isEnabled);
    }

    private void onConfirmClick(View view) {
        presenter.onRecoveryCodeConfirmed();
    }

    private void onRecoveryCodeEdited(String recoveryCodeString) {
        presenter.onRecoveryCodeEdited(recoveryCodeString);
    }

    @Override
    public void setVerificationError(UserFacingError error) {
        recoveryCodeBox.setError(error);
    }
}
