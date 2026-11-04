package io.muun.apollo.presentation.ui.settings;

import io.muun.apollo.R;
import io.muun.apollo.databinding.SignupWaitingForEmailVerificationFragmentBinding;
import io.muun.apollo.presentation.app.Email;
import io.muun.apollo.presentation.ui.base.SingleFragment;
import io.muun.apollo.presentation.ui.fragments.verify_email.VerifyEmailView;
import io.muun.apollo.presentation.ui.utils.StyledStringRes;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.viewbinding.ViewBinding;
import kotlin.jvm.functions.Function3;
import org.jetbrains.annotations.NotNull;

public class EmailWaitFragment extends SingleFragment<EmailWaitPresenter>
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
        binding().signupWaitingForEmailOpenEmailClient.setEnabled(
                Email.INSTANCE.hasEmailAppInstalled(requireContext())
        );
        binding().signupWaitingForEmailOpenEmailClient.setOnClickListener(
                v -> presenter.openEmailClient()
        );
    }

    @Override
    public void setEmail(@NotNull String email) {
        final StyledStringRes styledDesc = new StyledStringRes(
                requireContext(),
                R.string.signup_email_verify_explanation
        );

        binding().signupWaitingForEmailVerificationExplanation.setText(
                styledDesc.toCharSequence(email)
        );
    }

    @Override
    public void setLoading(boolean isLoading) {
        binding().signupWaitingForEmailVerificationLoading.setVisibility(
                isLoading ? View.VISIBLE : View.GONE
        );
        binding().signupWaitingForEmailVerificationTitle.setVisibility(
                !isLoading ? View.VISIBLE : View.GONE
        );
        binding().signupWaitingForEmailVerificationExplanation.setVisibility(
                !isLoading ? View.VISIBLE : View.GONE
        );
        binding().signupWaitingForEmailVerifyEmailIcon.setVisibility(
                !isLoading ? View.VISIBLE : View.GONE
        );
    }

    @Override
    public void handleInvalidLinkError() {
        setLoading(false);
        binding().signupWaitingForEmailVerifyEmailIcon
                .setImageResource(R.drawable.ic_envelope_error);
        binding().signupWaitingForEmailVerificationTitle.setText(R.string.email_link_error_title);
        binding().signupWaitingForEmailVerificationExplanation.setText(
                R.string.confirm_email_link_invalid
        );
    }

    @Override
    public void handleExpiredLinkError() {
        setLoading(false);
        binding().signupWaitingForEmailVerifyEmailIcon
                .setImageResource(R.drawable.ic_envelope_error);
        binding().signupWaitingForEmailVerificationTitle.setText(R.string.email_link_error_title);
        binding().signupWaitingForEmailVerificationExplanation.setText(
                R.string.confirm_email_link_expired
        );
    }
}
