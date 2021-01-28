package io.muun.apollo.presentation.ui.recovery_tool;

import io.muun.apollo.R;
import io.muun.apollo.presentation.ui.base.BaseFragment;
import io.muun.apollo.presentation.ui.base.BaseView;
import io.muun.apollo.presentation.ui.base.SingleFragmentActivity;
import io.muun.apollo.presentation.ui.fragments.recovery_tool.RecoveryToolFragment;
import io.muun.apollo.presentation.ui.view.MuunHeader;
import io.muun.apollo.presentation.ui.view.MuunHeader.Navigation;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import butterknife.BindView;

import javax.validation.constraints.NotNull;

public class RecoveryToolActivity extends SingleFragmentActivity<RecoveryToolActivityPresenter>
        implements BaseView {

    @BindView(R.id.header)
    MuunHeader header;

    /**
     * Creates an intent to launch this activity.
     */
    public static Intent getStartActivityIntent(@NotNull Context context) {
        return new Intent(context, RecoveryToolActivity.class);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.activity_recovery_tool;
    }

    @Override
    protected int getFragmentsContainer() {
        return R.id.fragment_container;
    }

    @Override
    protected void initializeUi() {
        super.initializeUi();

        header.attachToActivity(this);
        header.setBackgroundColor(Color.TRANSPARENT);
        header.showTitle(R.string.recovery_tool_header);
        header.setNavigation(Navigation.BACK);
    }

    @Override
    protected BaseFragment getInitialFragment() {
        return new RecoveryToolFragment();
    }

    @Override
    public MuunHeader getHeader() {
        return header;
    }

    @Override
    protected void inject() {
        getComponent().inject(this);
    }
}
