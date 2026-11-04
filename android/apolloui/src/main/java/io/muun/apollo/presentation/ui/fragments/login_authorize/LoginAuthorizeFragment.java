package io.muun.apollo.presentation.ui.fragments.login_authorize;

import io.muun.apollo.R;
import io.muun.apollo.databinding.SignupWaitingForEmailVerificationFragmentBinding;
import io.muun.apollo.presentation.app.Email;
import io.muun.apollo.presentation.ui.base.SingleFragment;
import io.muun.apollo.presentation.ui.fragments.verify_email.VerifyEmailView;
import io.muun.apollo.presentation.ui.utils.StyledStringRes;
import io.muun.apollo.presentation.ui.view.MuunHeader;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.viewbinding.ViewBinding;
import kotlin.jvm.functions.Function3;

public class LoginAuthorizeFragment
        extends SingleFragment<LoginAuthorizePresenter>
        implements VerifyEmailView {

    private SignupWaitingForEmailVerificationFragmentBinding binding() {
        return (SignupWaitingForEmailVerificationFragmentBinding) getBinding();
    }

    @Override
    protected Function3<LayoutInflater, ViewGroup, Boolean, ViewBinding> bindingInflater() {
        return SignupWaitingForEmailVerificationFragmentBinding::inflate;
    }

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected void initializeUi(View view) {
        binding().signupWaitingForEmailVerificationTitle.setText(R.string.signup_email_authorize);

        binding().signupWaitingForEmailOpenEmailClient
                .setEnabled(Email.INSTANCE.hasEmailAppInstalled(requireContext()));
        binding().signupWaitingForEmailOpenEmailClient
                .setOnClickListener(v -> presenter.openEmailClient());
    }

    @Override
    protected void setUpHeader() {
        final MuunHeader header = getParentActivity().getHeader();
        header.setNavigation(MuunHeader.Navigation.BACK);
        header.setElevated(true);
        header.showTitle(R.string.login_title);
    }

    @Override
    public boolean onBackPressed() {
        presenter.goBack();
        return true;
    }

    @Override
    public void setEmail(@NonNull String email) {

        final StyledStringRes styledDesc = new StyledStringRes(
                requireContext(),
                R.string.signup_email_verify_explanation
        );

        binding().signupWaitingForEmailVerificationExplanation
                .setText(styledDesc.toCharSequence(email));
    }

    @Override
    public void setLoading(boolean isLoading) {
        binding().signupWaitingForEmailVerificationLoading
                .setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding().signupWaitingForEmailVerificationTitle
                .setVisibility(!isLoading ? View.VISIBLE : View.GONE);
        binding().signupWaitingForEmailVerificationExplanation
                .setVisibility(!isLoading ? View.VISIBLE : View.GONE);
        binding().signupWaitingForEmailVerifyEmailIcon
                .setVisibility(!isLoading ? View.VISIBLE : View.GONE);
    }

    @Override
    public void handleInvalidLinkError() {
        setLoading(false);
        binding().signupWaitingForEmailVerifyEmailIcon
                .setImageResource(R.drawable.ic_envelope_error);
        binding().signupWaitingForEmailVerificationTitle.setText(R.string.email_link_error_title);
        binding().signupWaitingForEmailVerificationExplanation
                .setText(R.string.authorize_email_link_invalid);
    }

    @Override
    public void handleExpiredLinkError() {
        setLoading(false);
        binding().signupWaitingForEmailVerifyEmailIcon
                .setImageResource(R.drawable.ic_envelope_error);
        binding().signupWaitingForEmailVerificationTitle.setText(R.string.email_link_error_title);
        binding().signupWaitingForEmailVerificationExplanation
                .setText(R.string.authorize_email_link_expired);
    }
}
