package io.muun.apollo.presentation.ui.recovery_code.verify;

import io.muun.apollo.R;
import io.muun.apollo.databinding.VerifyRecoveryCodeFragmentBinding;
import io.muun.apollo.domain.errors.UserFacingError;
import io.muun.apollo.domain.libwallet.RecoveryCodeV2;
import io.muun.apollo.presentation.ui.base.SingleFragment;
import io.muun.apollo.presentation.ui.recovery_code.SetupRecoveryCodeActivity;
import io.muun.apollo.presentation.ui.view.MuunHeader;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.viewbinding.ViewBinding;
import kotlin.jvm.functions.Function3;

import java.util.List;

public class VerifyRecoveryCodeFragment extends SingleFragment<VerifyRecoveryCodePresenter>
        implements VerifyRecoveryCodeView {

    private VerifyRecoveryCodeFragmentBinding binding() {
        return (VerifyRecoveryCodeFragmentBinding) getBinding();
    }

    @Override
    protected Function3<LayoutInflater, ViewGroup, Boolean, ViewBinding> bindingInflater() {
        return VerifyRecoveryCodeFragmentBinding::inflate;
    }

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected void initializeUi(View view) {
        binding().recoveryCodeBox.setOnEditedListener(this::onRecoveryCodeEdited);
        binding().accept.setOnClickListener(this::onConfirmClick);
    }

    @Override
    public void setUpHeader() {
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
        onRecoveryCodeEdited(
                binding().recoveryCodeBox.getSegmentInputsContent()
        ); // re-trigger validation
        binding().recoveryCodeBox.requestFocusOnFirstEditableSegment();
    }

    @Override
    protected boolean blockScreenshots() {
        return true;
    }

    @Override
    public void setRecoveryCode(RecoveryCodeV2 recoveryCode) {
        binding().recoveryCodeBox.setRecoveryCode(recoveryCode);
    }

    @Override
    public void setSegmentsToVerify(List<Integer> segmentsToVerify) {
        for (int i = 0; i < RecoveryCodeV2.SEGMENT_COUNT; i++) {
            final boolean wasEditable = binding().recoveryCodeBox.isSegmentEditable(i);
            final boolean isEditable = segmentsToVerify.contains(i);

            binding().recoveryCodeBox.setSegmentEditable(i, isEditable);

            if (isEditable && !wasEditable) {
                binding().recoveryCodeBox.clearSegment(i);
            }
        }

        binding().recoveryCodeBox.requestFocusOnFirstEditableSegment();
        binding().recoveryCodeBox.setOnKeyboardNextListeners();
        binding().recoveryCodeBox.setOnKeyboardDoneListener(() -> {
            if (binding().accept.isEnabled()) {
                binding().accept.callOnClick();
            }
        });
    }

    @Override
    public void setConfirmEnabled(boolean isEnabled) {
        binding().accept.setEnabled(isEnabled);
    }

    private void onConfirmClick(View view) {
        presenter.onRecoveryCodeConfirmed();
    }

    private void onRecoveryCodeEdited(String recoveryCodeString) {
        presenter.onRecoveryCodeEdited(recoveryCodeString);
    }

    @Override
    public void setVerificationError(UserFacingError error) {
        binding().recoveryCodeBox.setError(error);
    }
}
