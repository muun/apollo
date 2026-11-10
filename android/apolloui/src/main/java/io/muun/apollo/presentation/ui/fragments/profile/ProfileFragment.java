package io.muun.apollo.presentation.ui.fragments.profile;

import io.muun.apollo.R;
import io.muun.apollo.databinding.SignupProfileFragmentBinding;
import io.muun.apollo.domain.errors.UserFacingError;
import io.muun.apollo.presentation.ui.base.SingleFragment;
import io.muun.apollo.presentation.ui.new_operation.TitleAndDescriptionDrawer;
import io.muun.apollo.presentation.ui.view.RichText;

import android.net.Uri;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.viewbinding.ViewBinding;
import kotlin.jvm.functions.Function3;

public class ProfileFragment extends SingleFragment<ProfilePresenter> implements ProfileView {

    private SignupProfileFragmentBinding binding() {
        return (SignupProfileFragmentBinding) getBinding();
    }

    @Override
    protected Function3<LayoutInflater, ViewGroup, Boolean, ViewBinding> bindingInflater() {
        return SignupProfileFragmentBinding::inflate;
    }

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected void initializeUi(View view) {
        binding().signupProfilePicture.setOnErrorListener(presenter::handleError);
        binding().signupProfilePicture.setOnChangeListener(this::onPictureChange);

        final String whyThisText = getString(R.string.signup_profile_why_this);

        final CharSequence content = TextUtils.concat(
                getString(R.string.signup_profile_explanation),
                ". ",
                new RichText(whyThisText).setLink(this::onWhyThisClick)
        );

        binding().signupProfileExplanation.setText(content);
        binding().signupContinue.setOnClickListener(v -> presenter.submitManualProfile(
                binding().signupProfileEditFirstName.getText().toString().trim(),
                binding().signupProfileEditLastName.getText().toString().trim(),
                binding().signupProfilePicture.getPictureUri()
        ));

        hideKeyboard(view);
    }

    @Override
    public void setFirstNameError(UserFacingError error) {
        binding().signupProfileEditFirstName.setError(error);

        if (error != null) {
            binding().signupProfileEditFirstName.requestFocusInput();
        }
    }

    @Override
    public void setLastNameError(UserFacingError error) {
        binding().signupProfileEditLastName.setError(error);

        if (error != null) {
            binding().signupProfileEditLastName.requestFocusInput();
        }
    }

    @Override
    public void setLoading(boolean isLoading) {
        binding().signupProfileEditFirstName.setEnabled(!isLoading);
        binding().signupProfileEditLastName.setEnabled(!isLoading);
        binding().signupProfilePicture.setEnabled(!isLoading);
        binding().signupContinue.setLoading(isLoading);

        if (isLoading) {
            hideKeyboard(getView());
        }
    }

    private void onPictureChange(Uri uri) {
        binding().signupProfilePicture.setPicture(uri);
    }

    private void onWhyThisClick() {
        final TitleAndDescriptionDrawer dialog = new TitleAndDescriptionDrawer();
        dialog.setTitle(R.string.signup_profile_why_this_title);
        dialog.setDescription(getString(R.string.signup_profile_why_this_explanation));
        dialog.show(getParentFragmentManager(), null);

        presenter.reportShowProfileInfo();
    }
}
