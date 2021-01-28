package io.muun.apollo.presentation.ui.recovery_code.show;

import io.muun.apollo.R;
import io.muun.apollo.domain.libwallet.RecoveryCodeV2;
import io.muun.apollo.presentation.ui.base.SingleFragment;
import io.muun.apollo.presentation.ui.recovery_code.SetupRecoveryCodeActivity;
import io.muun.apollo.presentation.ui.view.MuunHeader;
import io.muun.apollo.presentation.ui.view.MuunRecoveryCodeBox;

import android.view.View;
import android.widget.TextView;
import butterknife.BindView;
import butterknife.OnClick;

public class ShowRecoveryCodeFragment extends SingleFragment<ShowRecoveryCodePresenter>
        implements ShowRecoveryCodeView {

    @BindView(R.id.explanation)
    TextView explanationView;

    @BindView(R.id.recovery_code_box)
    MuunRecoveryCodeBox recoveryCodeBox;

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.show_recovery_code_fragment;
    }

    @Override
    protected void initializeUi(View view) {
        super.initializeUi(view);

        final String indicatorText = getString(
                R.string.set_up_rc_step_count,
                1,
                SetupRecoveryCodeActivity.SET_UP_RC_STEP_COUNT
        );

        final MuunHeader header = getParentActivity().getHeader();
        header.setIndicatorText(indicatorText);
        header.setElevated(true);
        header.setNavigation(MuunHeader.Navigation.EXIT);
    }

    @Override
    public void setRecoveryCode(RecoveryCodeV2 recoveryCode) {
        recoveryCodeBox.setRecoveryCode(recoveryCode);
    }

    @OnClick(R.id.recovery_code_continue)
    public void onAcceptClick() {
        presenter.continueToVerification();
    }

    @Override
    public boolean onBackPressed() {
        presenter.showAbortDialog();
        return true;
    }
}
