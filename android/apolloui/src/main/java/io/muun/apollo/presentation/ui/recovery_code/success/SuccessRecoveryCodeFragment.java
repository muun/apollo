package io.muun.apollo.presentation.ui.recovery_code.success;

import io.muun.apollo.R;
import io.muun.apollo.domain.model.user.User;
import io.muun.apollo.presentation.ui.fragments.single_action.SingleActionFragment;

import android.view.View;

public class SuccessRecoveryCodeFragment
        extends SingleActionFragment<SuccessRecoveryCodePresenter>
        implements SuccessRecoveryCodeView {

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected void initializeUi(View view) {
        super.initializeUi(view);
        getParentActivity().getHeader().setVisibility(View.GONE);
    }

    @Override
    protected int getImageRes() {
        return R.drawable.tick;
    }

    @Override
    protected String getTitle() {
        return getString(R.string.recovery_code_success_message);
    }

    @Override
    protected String getDescription() {
        return getString(R.string.recovery_code_success_description);
    }

    @Override
    protected int getActionLabelRes() {
        return R.string.recovery_code_success_action;
    }

    @Override
    public void setTexts(User user) {
        if (user.hasPassword) {
            description.setText(R.string.recovery_code_success_description);

        } else {
            description.setText(R.string.recovery_code_success_description_email_skipped);
        }
    }

    @Override
    public boolean onBackPressed() {
        finishActivity();
        return true;
    }
}
