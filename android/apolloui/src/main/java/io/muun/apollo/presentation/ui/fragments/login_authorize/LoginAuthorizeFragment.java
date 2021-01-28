package io.muun.apollo.presentation.ui.fragments.login_authorize;

import io.muun.apollo.R;
import io.muun.apollo.presentation.ui.base.SingleFragment;
import io.muun.apollo.presentation.ui.fragments.verify_email.VerifyEmailView;
import io.muun.apollo.presentation.ui.utils.StyledStringRes;
import io.muun.apollo.presentation.ui.view.LoadingView;
import io.muun.apollo.presentation.ui.view.MuunButton;
import io.muun.apollo.presentation.ui.view.MuunHeader;
import io.muun.apollo.presentation.ui.view.MuunHeader.Navigation;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import butterknife.BindView;
import butterknife.OnClick;

public class LoginAuthorizeFragment
        extends SingleFragment<LoginAuthorizePresenter>
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
    protected int getLayoutResource() {
        return R.layout.signup_waiting_for_email_verification_fragment;
    }

    @Override
    protected void initializeUi(View view) {
        super.initializeUi(view);

        final MuunHeader header = getParentActivity().getHeader();
        header.setNavigation(Navigation.BACK);
        header.setElevated(true);
        header.showTitle(R.string.login_title);

        titleView.setText(R.string.signup_email_authorize);

        openEmailAppButton.setEnabled(presenter.hasEmailAppInstalled());
    }

    @Override
    public boolean onBackPressed() {
        presenter.goBack();
        return true;
    }

    @OnClick(R.id.signup_waiting_for_email_open_email_client)
    public void onOpenEmailClient() {
        presenter.onOpenEmailClient();
    }

    @Override
    public void setEmail(String email) {

        final StyledStringRes styledDesc = new StyledStringRes(
                getContext(),
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
        descriptionView.setText(R.string.authorize_email_link_invalid);
    }

    @Override
    public void handleExpiredLinkError() {
        setLoading(false);
        emailIcon.setImageResource(R.drawable.ic_envelope_error);
        titleView.setText(R.string.email_link_error_title);
        descriptionView.setText(R.string.authorize_email_link_expired);
    }
}
