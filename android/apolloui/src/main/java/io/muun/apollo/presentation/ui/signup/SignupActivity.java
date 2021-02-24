package io.muun.apollo.presentation.ui.signup;

import io.muun.apollo.R;
import io.muun.apollo.domain.model.SignupStep;
import io.muun.apollo.presentation.ui.activity.extension.ApplicationLockExtension;
import io.muun.apollo.presentation.ui.base.BaseFragment;
import io.muun.apollo.presentation.ui.base.SingleFragmentActivity;
import io.muun.apollo.presentation.ui.fragments.enter_password.EnterPasswordFragment;
import io.muun.apollo.presentation.ui.fragments.enter_recovery_code.EnterRecoveryCodeFragment;
import io.muun.apollo.presentation.ui.fragments.landing.LandingFragment;
import io.muun.apollo.presentation.ui.fragments.login_authorize.LoginAuthorizeFragment;
import io.muun.apollo.presentation.ui.fragments.login_email.LoginEmailFragment;
import io.muun.apollo.presentation.ui.fragments.rc_only_login.RcOnlyLoginFragment;
import io.muun.apollo.presentation.ui.fragments.rc_only_login_auth.RcLoginEmailAuthorizeFragment;
import io.muun.apollo.presentation.ui.fragments.sync.SyncFragment;
import io.muun.apollo.presentation.ui.view.MuunHeader;
import io.muun.apollo.presentation.ui.view.MuunHeader.Navigation;
import io.muun.common.exception.MissingCaseError;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import androidx.fragment.app.Fragment;
import butterknife.BindView;

import javax.validation.constraints.NotNull;

public class SignupActivity extends SingleFragmentActivity<SignupPresenter>
        implements SignupView {

    /**
     * Creates an intent to launch this activity.
     */
    public static Intent getStartActivityIntent(@NotNull Context context) {
        return new Intent(context, SignupActivity.class);
    }

    @BindView(R.id.signup_header)
    MuunHeader header;

    @Override
    protected void setUpExtensions() {
        super.setUpExtensions();
        getExtension(ApplicationLockExtension.class).setRequireUnlock(false);
    }

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected void initializeUi() {

        header.attachToActivity(this);
        header.setBackgroundColor(Color.TRANSPARENT);
        header.setNavigation(Navigation.NONE); // let each fragment decide
        header.hideTitle();
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.signup_activity;
    }

    @Override
    protected int getFragmentsContainer() {
        return R.id.signup_frame_container;
    }

    @Override
    protected BaseFragment getInitialFragment() {
        return LandingFragment.newInstanceWithAnimation();
    }

    @Override
    public void changeStep(SignupStep step, SignupStep previousStep) {
        replaceFragment(createStepFragment(step, previousStep), false);
    }

    public MuunHeader getHeader() {
        return header;
    }

    private Fragment createStepFragment(SignupStep step, SignupStep previousStep) {
        switch (step) {
            case START:
                return LandingFragment.newInstance();

            case LOGIN_EMAIL:
                return new LoginEmailFragment();

            case LOGIN_RECOVERY_CODE_ONLY:
                return new RcOnlyLoginFragment();

            case LOGIN_RECOVERY_CODE_EMAIL_AUTH:
                return new RcLoginEmailAuthorizeFragment();

            case LOGIN_PASSWORD:
                return new EnterPasswordFragment();

            case LOGIN_WAIT_VERIFICATION:
                return new LoginAuthorizeFragment();

            case SYNC:
                return SyncFragment.create(previousStep);

            case LOGIN_RECOVERY_CODE:
                return new EnterRecoveryCodeFragment();

            default:
                throw new MissingCaseError(step);
        }
    }
}
