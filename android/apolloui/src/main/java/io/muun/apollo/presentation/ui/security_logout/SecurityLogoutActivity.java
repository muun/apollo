package io.muun.apollo.presentation.ui.security_logout;

import io.muun.apollo.databinding.SecurityLogoutActivityBinding;
import io.muun.apollo.presentation.ui.activity.extension.ApplicationLockExtension;
import io.muun.apollo.presentation.ui.base.BaseActivity;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import androidx.viewbinding.ViewBinding;
import kotlin.jvm.functions.Function1;

import jakarta.validation.constraints.NotNull;

public class SecurityLogoutActivity extends BaseActivity<SecurityLogoutPresenter> {

    /**
     * Creates an intent to launch this activity.
     */
    public static Intent getStartActivityIntent(@NotNull Context context) {
        return new Intent(context, SecurityLogoutActivity.class);
    }

    private SecurityLogoutActivityBinding binding() {
        return (SecurityLogoutActivityBinding) getBinding();
    }

    @Override
    protected Function1<LayoutInflater, ViewBinding> bindingInflater() {
        return SecurityLogoutActivityBinding::inflate;
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
    protected void initializeUi() {
        binding().securityLogoutSignIn.setOnClickListener(v -> presenter.goToSignIn());
    }
}
