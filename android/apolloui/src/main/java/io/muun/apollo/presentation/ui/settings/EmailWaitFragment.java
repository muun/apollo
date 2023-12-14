package io.muun.apollo.presentation.ui.settings;

import io.muun.apollo.R;
import io.muun.apollo.presentation.app.Email;
import io.muun.apollo.presentation.ui.base.SingleFragment;
import io.muun.apollo.presentation.ui.fragments.verify_email.VerifyEmailView;
import io.muun.apollo.presentation.ui.utils.StyledStringRes;
import io.muun.apollo.presentation.ui.view.LoadingView;
import io.muun.apollo.presentation.ui.view.MuunButton;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import butterknife.BindView;
import org.jetbrains.annotations.NotNull;


public class EmailWaitFragment extends SingleFragment<EmailWaitPresenter>
        implements VerifyEmailView {

    @BindView(R.id.signup_waiting_for_email_open_email_client)
    MuunButton openEmailAppButton;

    @BindView(R.id.signup_waiting_for_email_verification_title)
    TextView titleView;

    @BindView(R.id.signup_waiting_for_email_verification_explanation)
    TextView descriptionView;

    @BindView(R.id.signup_waiting_for_email_verify_email_icon)
    ImageView emailIcon;

    @BindView(R.id.signup_waiting_for_email_verification_loading)
    LoadingView loadingView;

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected void initializeUi(View view) {
        openEmailAppButton.setEnabled(Email.INSTANCE.hasEmailAppInstalled(requireContext()));
        openEmailAppButton.setOnClickListener(v -> presenter.openEmailClient());
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.signup_waiting_for_email_verification_fragment;
    }

    @Override
    public void setEmail(@NotNull String email) {
        final StyledStringRes styledDesc = new StyledStringRes(
                requireContext(),
                R.string.signup_email_verify_explanation
        );

        descriptionView.setText(styledDesc.toCharSequence(email));
    }

    @Override
    public void setLoading(boolean isLoading) {
        loadingView.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        titleView.setVisibility(!isLoading ? View.VISIBLE : View.GONE);
        descriptionView.setVisibility(!isLoading ? View.VISIBLE : View.GONE);
        emailIcon.setVisibility(!isLoading ? View.VISIBLE : View.GONE);
    }

    @Override
    public void handleInvalidLinkError() {
        setLoading(false);
        emailIcon.setImageResource(R.drawable.ic_envelope_error);
        titleView.setText(R.string.email_link_error_title);
        descriptionView.setText(R.string.confirm_email_link_invalid);
    }

    @Override
    public void handleExpiredLinkError() {
        setLoading(false);
        emailIcon.setImageResource(R.drawable.ic_envelope_error);
        titleView.setText(R.string.email_link_error_title);
        descriptionView.setText(R.string.confirm_email_link_expired);
    }
}
