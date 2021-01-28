package io.muun.apollo.presentation.ui.security_logout;

import io.muun.apollo.R;
import io.muun.apollo.presentation.ui.activity.extension.ApplicationLockExtension;
import io.muun.apollo.presentation.ui.base.BaseActivity;

import android.content.Context;
import android.content.Intent;
import butterknife.OnClick;

import javax.validation.constraints.NotNull;


public class SecurityLogoutActivity extends BaseActivity<SecurityLogoutPresenter> {

    /**
     * Creates an intent to launch this activity.
     */
    public static Intent getStartActivityIntent(@NotNull Context context) {
        return new Intent(context, SecurityLogoutActivity.class);
    }

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected void setUpExtensions() {
        super.setUpExtensions();
        getExtension(ApplicationLockExtension.class).setRequireUnlock(false);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.security_logout_activity;
    }

    @OnClick(R.id.security_logout_sign_in)
    public void onSignInClick() {
        presenter.goToSignIn();
    }
}
