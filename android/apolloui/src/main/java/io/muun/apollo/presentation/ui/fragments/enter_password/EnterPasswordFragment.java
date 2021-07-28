package io.muun.apollo.presentation.ui.fragments.enter_password;

import io.muun.apollo.R;
import io.muun.apollo.domain.errors.UserFacingError;
import io.muun.apollo.presentation.ui.activity.extension.MuunDialog;
import io.muun.apollo.presentation.ui.base.SingleFragment;
import io.muun.apollo.presentation.ui.view.HtmlTextView;
import io.muun.apollo.presentation.ui.view.MuunButton;
import io.muun.apollo.presentation.ui.view.MuunHeader;
import io.muun.apollo.presentation.ui.view.MuunHeader.Navigation;
import io.muun.apollo.presentation.ui.view.MuunTextInput;

import android.view.View;
import butterknife.BindView;
import butterknife.OnClick;

public class EnterPasswordFragment extends SingleFragment<EnterPasswordPresenter>
        implements EnterPasswordView {

    @BindView(R.id.signup_unlock_edit_password)
    MuunTextInput password;

    @BindView(R.id.signup_continue)
    MuunButton continueButton;

    @BindView(R.id.signup_forgot_password)
    MuunButton forgotPasswordButton;

    @BindView(R.id.signup_unlock_reminder)
    HtmlTextView reminder;

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
        super.initializeUi(view);

        setUpHeader();

        password.setPasswordRevealEnabled(true);
        password.setOnKeyboardNextListener(continueButton::callOnClick);
        password.setOnChangeListener((ignored) -> validateInput());
        validateInput();
    }

    private void setUpHeader() {
        final MuunHeader header = getParentActivity().getHeader();
        header.setNavigation(Navigation.BACK);
        header.showTitle(R.string.login_title);
        header.setElevated(true);
    }

    private void validateInput() {
        continueButton.setEnabled(!presenter.isEmptyPassword(password.getText().toString()));
    }

    @Override
    public void onResume() {
        super.onResume();
        password.requestFocusInput();
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
                .positiveButton(R.string.abort, presenter::abortSignIn)
                .negativeButton(R.string.cancel, null)
                .build();

        getParentActivity().showDialog(muunDialog);

        return true;
    }

    @Override
    public void setLoading(boolean isLoading) {
        password.setEnabled(!isLoading);
        continueButton.setLoading(isLoading);
    }

    @OnClick(R.id.signup_continue)
    void onContinueButtonClick() {
        presenter.submitPassword(password.getText().toString());
    }

    @OnClick(R.id.signup_forgot_password)
    void onForgotPasswordButtonClick() {
        presenter.navigateToForgotPassword();
    }

    @Override
    public void setPasswordError(UserFacingError error) {
        password.setError(error);

        if (error != null) {
            password.requestFocusInput();
        }
    }

    @Override
    public void setReminderVisible(boolean isVisible) {
        setVisible(reminder, isVisible);
    }

    @Override
    public void setForgotPasswordVisible(boolean isVisible) {
        setVisible(forgotPasswordButton, isVisible);
    }
}
