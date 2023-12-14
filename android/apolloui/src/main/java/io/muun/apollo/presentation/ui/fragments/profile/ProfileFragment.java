package io.muun.apollo.presentation.ui.fragments.profile;

import io.muun.apollo.R;
import io.muun.apollo.domain.errors.UserFacingError;
import io.muun.apollo.presentation.ui.base.SingleFragment;
import io.muun.apollo.presentation.ui.new_operation.TitleAndDescriptionDrawer;
import io.muun.apollo.presentation.ui.view.HtmlTextView;
import io.muun.apollo.presentation.ui.view.MuunButton;
import io.muun.apollo.presentation.ui.view.MuunPictureInput;
import io.muun.apollo.presentation.ui.view.MuunTextInput;
import io.muun.apollo.presentation.ui.view.RichText;

import android.net.Uri;
import android.text.TextUtils;
import android.view.View;
import butterknife.BindView;
import butterknife.OnClick;

public class ProfileFragment extends SingleFragment<ProfilePresenter> implements ProfileView {

    @BindView(R.id.signup_profile_explanation)
    HtmlTextView explanation;

    @BindView(R.id.signup_profile_picture)
    MuunPictureInput profilePicture;

    @BindView(R.id.signup_profile_edit_first_name)
    MuunTextInput firstName;

    @BindView(R.id.signup_profile_edit_last_name)
    MuunTextInput lastName;

    @BindView(R.id.signup_continue)
    MuunButton continueButton;

    @Override
    protected int getLayoutResource() {
        return R.layout.signup_profile_fragment;
    }

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected void initializeUi(View view) {
        profilePicture.setOnErrorListener(presenter::handleError);
        profilePicture.setOnChangeListener(this::onPictureChange);

        final String whyThisText = getString(R.string.signup_profile_why_this);

        final CharSequence content = TextUtils.concat(
                getString(R.string.signup_profile_explanation),
                ". ",
                new RichText(whyThisText).setLink(this::onWhyThisClick)
        );

        explanation.setText(content);

        hideKeyboard(view);
    }

    @OnClick(R.id.signup_continue)
    void onContinueButtonClick() {
        presenter.submitManualProfile(
                firstName.getText().toString().trim(),
                lastName.getText().toString().trim(),
                profilePicture.getPictureUri()
        );
    }

    @Override
    public void setFirstNameError(UserFacingError error) {
        firstName.setError(error);

        if (error != null) {
            firstName.requestFocusInput();
        }
    }

    @Override
    public void setLastNameError(UserFacingError error) {
        lastName.setError(error);

        if (error != null) {
            lastName.requestFocusInput();
        }
    }

    @Override
    public void setLoading(boolean isLoading) {
        firstName.setEnabled(!isLoading);
        lastName.setEnabled(!isLoading);
        profilePicture.setEnabled(!isLoading);
        continueButton.setLoading(isLoading);

        if (isLoading) {
            hideKeyboard(getView());
        }
    }

    private void onPictureChange(Uri uri) {
        profilePicture.setPicture(uri);
    }

    private void onWhyThisClick() {
        final TitleAndDescriptionDrawer dialog = new TitleAndDescriptionDrawer();
        dialog.setTitle(R.string.signup_profile_why_this_title);
        dialog.setDescription(getString(R.string.signup_profile_why_this_explanation));
        dialog.show(getParentFragmentManager(), null);

        presenter.reportShowProfileInfo();
    }
}
