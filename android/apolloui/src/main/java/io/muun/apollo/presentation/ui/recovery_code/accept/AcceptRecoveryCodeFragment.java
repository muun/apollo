package io.muun.apollo.presentation.ui.recovery_code.accept;

import io.muun.apollo.R;
import io.muun.apollo.domain.model.User;
import io.muun.apollo.presentation.ui.base.SingleFragment;
import io.muun.apollo.presentation.ui.recovery_code.SetupRecoveryCodeActivity;
import io.muun.apollo.presentation.ui.view.MuunButton;
import io.muun.apollo.presentation.ui.view.MuunHeader;

import android.view.View;
import android.widget.CheckBox;
import butterknife.BindView;
import butterknife.OnCheckedChanged;
import butterknife.OnClick;

public class AcceptRecoveryCodeFragment extends SingleFragment<AcceptRecoveryCodePresenter>
        implements AcceptRecoveryCodeView {

    @BindView(R.id.recovery_code_condition_1)
    CheckBox condition1;

    @BindView(R.id.recovery_code_condition_2)
    CheckBox condition2;

    @BindView(R.id.recovery_code_accept)
    MuunButton acceptButton;

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.accept_recovery_code_fragment;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateAcceptButtonState();
        hideKeyboard(getView());
    }

    @Override
    protected void initializeUi(View view) {
        super.initializeUi(view);

        final String indicatorText = getString(
                R.string.set_up_rc_step_count,
                3,
                SetupRecoveryCodeActivity.SET_UP_RC_STEP_COUNT
        );

        final MuunHeader header = getParentActivity().getHeader();
        header.setIndicatorText(indicatorText);
        header.setElevated(true);
        header.setNavigation(MuunHeader.Navigation.EXIT);
    }

    @Override
    public void setTexts(User user) {
        if (!user.hasPassword) {
            condition1.setText(R.string.recovery_code_verify_accept_condition_1_skipped_email);
        }
    }

    @Override
    public boolean onBackPressed() {
        if (!acceptButton.isLoading()) {
            presenter.showAbortDialog();
        }

        return true;
    }

    @OnCheckedChanged({R.id.recovery_code_condition_1, R.id.recovery_code_condition_2})
    void onConditionCheckedChanged() {
        updateAcceptButtonState();
    }

    @OnClick(R.id.recovery_code_accept)
    void onAcceptClick() {
        presenter.finishSetup();
    }

    public void setLoading(boolean isLoading) {
        acceptButton.setLoading(isLoading);
    }

    private void updateAcceptButtonState() {
        acceptButton.setEnabled(
                condition1.isChecked() && condition2.isChecked()
        );
    }
}
