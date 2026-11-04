package io.muun.apollo.presentation.ui.recovery_tool;

import io.muun.apollo.R;
import io.muun.apollo.databinding.ActivityRecoveryToolBinding;
import io.muun.apollo.presentation.ui.base.BaseView;
import io.muun.apollo.presentation.ui.base.SingleFragment;
import io.muun.apollo.presentation.ui.base.SingleFragmentActivity;
import io.muun.apollo.presentation.ui.fragments.recovery_tool.RecoveryToolFragment;
import io.muun.apollo.presentation.ui.fragments.recovery_tool.RecoveryToolPresenter;
import io.muun.apollo.presentation.ui.view.MuunHeader;
import io.muun.apollo.presentation.ui.view.MuunHeader.Navigation;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import androidx.viewbinding.ViewBinding;
import kotlin.jvm.functions.Function1;

import jakarta.validation.constraints.NotNull;

public class RecoveryToolActivity extends SingleFragmentActivity<RecoveryToolActivityPresenter>
        implements BaseView {

    private ActivityRecoveryToolBinding binding() {
        return (ActivityRecoveryToolBinding) getBinding();
    }

    @Override
    protected Function1<LayoutInflater, ViewBinding> bindingInflater() {
        return ActivityRecoveryToolBinding::inflate;
    }

    /**
     * Creates an intent to launch this activity.
     */
    public static Intent getStartActivityIntent(@NotNull Context context) {
        return new Intent(context, RecoveryToolActivity.class);
    }

    @Override
    protected int getFragmentsContainer() {
        return R.id.fragment_container;
    }

    @Override
    protected void initializeUi() {
        super.initializeUi();

        binding().header.attachToActivity(this);
        binding().header.setBackgroundColor(Color.TRANSPARENT);
        binding().header.showTitle(R.string.recovery_tool_header);
        binding().header.setNavigation(Navigation.BACK);
    }

    @Override
    protected SingleFragment<RecoveryToolPresenter> getInitialFragment() {
        return new RecoveryToolFragment();
    }

    @Override
    public MuunHeader getHeader() {
        return binding().header;
    }

    @Override
    protected void inject() {
        getComponent().inject(this);
    }
}
