package io.muun.apollo.presentation.ui.settings.edit_password;

import io.muun.apollo.R;
import io.muun.apollo.presentation.ui.base.SingleFragment;
import io.muun.apollo.presentation.ui.base.SingleFragmentActivity;
import io.muun.apollo.presentation.ui.view.MuunHeader;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import butterknife.BindView;

import javax.validation.constraints.NotNull;

public class EditPasswordActivity extends SingleFragmentActivity<EditPasswordPresenter>
        implements EditPasswordView {

    /**
     * Creates an intent to launch this activity.
     */
    public static Intent getStartActivityIntent(@NotNull Context context) {
        return new Intent(context, EditPasswordActivity.class);
    }

    @BindView(R.id.edit_password_header)
    MuunHeader header;

    @Override protected void inject() {
        getComponent().inject(this);
    }

    @Override protected int getLayoutResource() {
        return R.layout.edit_password_activity;
    }

    @Override
    protected void initializeUi() {
        super.initializeUi();

        header.attachToActivity(this);
        header.setBackgroundColor(Color.TRANSPARENT);
        header.setNavigation(MuunHeader.Navigation.BACK);
        header.showTitle(R.string.settings_password);
        header.setElevated(true);
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
        return header;
    }
}
