package io.muun.apollo.presentation.ui.signup;

import io.muun.apollo.R;
import io.muun.apollo.databinding.SignupActivityBinding;
import io.muun.apollo.domain.model.SignupStep;
import io.muun.apollo.presentation.ui.activity.extension.ApplicationLockExtension;
import io.muun.apollo.presentation.ui.base.SingleFragment;
import io.muun.apollo.presentation.ui.base.SingleFragmentActivity;
import io.muun.apollo.presentation.ui.fragments.enter_password.EnterPasswordFragment;
import io.muun.apollo.presentation.ui.fragments.enter_recovery_code.EnterRecoveryCodeFragment;
import io.muun.apollo.presentation.ui.fragments.landing.LandingFragment;
import io.muun.apollo.presentation.ui.fragments.landing.LandingPresenter;
import io.muun.apollo.presentation.ui.fragments.login_authorize.LoginAuthorizeFragment;
import io.muun.apollo.presentation.ui.fragments.login_email.LoginEmailFragment;
import io.muun.apollo.presentation.ui.fragments.rc_only_login.RcOnlyLoginFragment;
import io.muun.apollo.presentation.ui.fragments.rc_only_login_auth.RcLoginEmailAuthorizeFragment;
import io.muun.apollo.presentation.ui.fragments.sync.SyncFragment;
import io.muun.apollo.presentation.ui.signup.unverified_rc.UnverifiedRcWarningFragment;
import io.muun.apollo.presentation.ui.view.MuunHeader;
import io.muun.apollo.presentation.ui.view.MuunHeader.Navigation;
import io.muun.common.exception.MissingCaseError;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import androidx.fragment.app.Fragment;
import androidx.viewbinding.ViewBinding;
import kotlin.jvm.functions.Function1;

import jakarta.validation.constraints.NotNull;

public class SignupActivity extends SingleFragmentActivity<SignupPresenter>
        implements SignupView {

    /**
     * Creates an intent to launch this activity.
     */
    public static Intent getStartActivityIntent(@NotNull Context context) {
        return new Intent(context, SignupActivity.class);
    }

    private SignupActivityBinding binding() {
        return (SignupActivityBinding) getBinding();
    }

    @Override
    protected Function1<LayoutInflater, ViewBinding> bindingInflater() {
        return SignupActivityBinding::inflate;
    }

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
        binding().signupHeader.attachToActivity(this);
        binding().signupHeader.setBackgroundColor(Color.TRANSPARENT);
        binding().signupHeader.setNavigation(Navigation.NONE); // let each fragment decide
        binding().signupHeader.hideTitle();
    }

    @Override
    protected int getFragmentsContainer() {
        return R.id.signup_frame_container;
    }

    @Override
    protected SingleFragment<LandingPresenter> getInitialFragment() {
        return LandingFragment.newInstanceWithAnimation();
    }

    @Override
    public void changeStep(SignupStep step) {
        replaceFragment(createStepFragment(step), false);
    }

    public MuunHeader getHeader() {
        return binding().signupHeader;
    }

    private Fragment createStepFragment(SignupStep step) {
        switch (step) {
            case START:
                return LandingFragment.newInstance();

            case LOGIN_EMAIL:
                return new LoginEmailFragment();

            case LOGIN_RECOVERY_CODE_ONLY:
                return new RcOnlyLoginFragment();

            case LOGIN_RECOVERY_CODE_EMAIL_AUTH:
                return new RcLoginEmailAuthorizeFragment();

            case LOGIN_WAIT_VERIFICATION:
                return new LoginAuthorizeFragment();

            case LOGIN_PASSWORD:
                return new EnterPasswordFragment();

            case SYNC:
                return new SyncFragment();

            case UNVERIFIED_RC_WARNING:
                return new UnverifiedRcWarningFragment();

            case LOGIN_RECOVERY_CODE:
                return new EnterRecoveryCodeFragment();

            default:
                throw new MissingCaseError(step);
        }
    }
}
