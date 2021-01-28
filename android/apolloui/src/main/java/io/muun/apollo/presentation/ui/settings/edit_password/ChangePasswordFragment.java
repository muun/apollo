package io.muun.apollo.presentation.ui.settings.edit_password;

import io.muun.apollo.R;
import io.muun.apollo.domain.errors.UserFacingError;
import io.muun.apollo.presentation.ui.activity.extension.MuunDialog;
import io.muun.apollo.presentation.ui.view.MuunButton;
import io.muun.apollo.presentation.ui.view.MuunTextInput;

import android.app.Activity;
import android.view.View;
import android.widget.CheckBox;
import butterknife.BindView;
import butterknife.OnCheckedChanged;
import butterknife.OnClick;

public class ChangePasswordFragment extends BaseEditPasswordFragment<ChangePasswordPresenter>
        implements ChangePasswordView {

    @BindView(R.id.change_password)
    MuunTextInput password;

    @BindView(R.id.change_password_condition)
    CheckBox condition;

    @BindView(R.id.change_password_continue)
    MuunButton continueButton;

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.change_password_fragment;
    }

    @Override
    protected void initializeUi(View view) {
        super.initializeUi(view);

        password.setPasswordRevealEnabled(true);

        continueButton.setEnabled(false);
    }

    @Override
    public boolean onBackPressed() {

        final MuunDialog muunDialog = new MuunDialog.Builder()
                .title(R.string.change_password_abort_dialog_title)
                .message(R.string.change_password_abort_dialog_message)
                .positiveButton(R.string.change_password_abort_dialog_yes, this::abort)
                .negativeButton(R.string.change_password_abort_dialog_no, null)
                .build();

        getParentActivity().showDialog(muunDialog);

        return true;
    }

    private void abort() {
        safeGetParentActivity().ifPresent(Activity::finish);
    }

    @Override
    public void setLoading(boolean loading) {
        continueButton.setLoading(loading);
        condition.setEnabled(!loading);
    }

    @Override
    public void setPasswordError(UserFacingError error) {
        password.clearError();

        if (error != null) {
            password.setError(error);
            focusInput(password);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        focusInput(password);
    }

    @Override
    protected boolean blockScreenshots() {
        return true;
    }

    @OnCheckedChanged(R.id.change_password_condition)
    public void onConditionCheckedChanged() {
        continueButton.setEnabled(condition.isChecked());
    }

    @OnClick(R.id.change_password_continue)
    void onContinueButtonClick() {
        presenter.submitPassword(password.getText().toString());
    }
}
