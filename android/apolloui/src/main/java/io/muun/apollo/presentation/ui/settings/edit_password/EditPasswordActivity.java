package io.muun.apollo.presentation.ui.settings.edit_password;

import io.muun.apollo.R;
import io.muun.apollo.databinding.EditPasswordActivityBinding;
import io.muun.apollo.presentation.ui.base.SingleFragment;
import io.muun.apollo.presentation.ui.base.SingleFragmentActivity;
import io.muun.apollo.presentation.ui.view.MuunHeader;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import androidx.viewbinding.ViewBinding;
import kotlin.jvm.functions.Function1;

import jakarta.validation.constraints.NotNull;

public class EditPasswordActivity extends SingleFragmentActivity<EditPasswordPresenter>
        implements EditPasswordView {

    /**
     * Creates an intent to launch this activity.
     */
    public static Intent getStartActivityIntent(@NotNull Context context) {
        return new Intent(context, EditPasswordActivity.class);
    }

    private EditPasswordActivityBinding binding() {
        return (EditPasswordActivityBinding) getBinding();
    }

    @Override
    protected Function1<LayoutInflater, ViewBinding> bindingInflater() {
        return EditPasswordActivityBinding::inflate;
    }

    @Override protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected void initializeUi() {
        super.initializeUi();

        binding().editPasswordHeader.attachToActivity(this);
        binding().editPasswordHeader.setBackgroundColor(Color.TRANSPARENT);
        binding().editPasswordHeader.setNavigation(MuunHeader.Navigation.BACK);
        binding().editPasswordHeader.showTitle(R.string.settings_password);
        binding().editPasswordHeader.setElevated(true);
    }

    @Override
    protected int getFragmentsContainer() {
        return R.id.fragment_container;
    }

    @Override
    protected SingleFragment getInitialFragment() {
        return new StartPasswordChangeFragment();
    }

    public MuunHeader getHeader() {
        return binding().editPasswordHeader;
    }
}
