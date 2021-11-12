package io.muun.apollo.presentation.ui.recovery_code;

import io.muun.apollo.R;
import io.muun.apollo.domain.model.user.User;
import io.muun.apollo.presentation.ui.activity.extension.MuunDialog;
import io.muun.apollo.presentation.ui.base.BaseFragment;
import io.muun.apollo.presentation.ui.base.SingleFragmentActivity;
import io.muun.apollo.presentation.ui.recovery_code.priming.PrimingRecoveryCodeFragment;
import io.muun.apollo.presentation.ui.view.MuunHeader;
import io.muun.apollo.presentation.ui.view.MuunHeader.Navigation;

import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import butterknife.BindView;

import javax.validation.constraints.NotNull;

public class SetupRecoveryCodeActivity extends SingleFragmentActivity<SetupRecoveryCodePresenter>
        implements SetupRecoveryCodeView {

    public static final int SET_UP_RC_STEP_COUNT = 3;

    /**
     * Creates an intent to launch this activity.
     */
    public static Intent getStartActivityIntent(@NotNull Context context) {
        return new Intent(context, SetupRecoveryCodeActivity.class);
    }

    @BindView(R.id.recovery_code_header)
    MuunHeader header;

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.recovery_code_activity;
    }

    @Override
    protected int getFragmentsContainer() {
        return R.id.fragment_container;
    }

    @Override
    public MuunHeader getHeader() {
        return header;
    }

    @Override
    protected BaseFragment getInitialFragment() {
        return new PrimingRecoveryCodeFragment();
    }

    @Override
    protected void initializeUi() {
        super.initializeUi();

        header.attachToActivity(this);
        header.setNavigation(Navigation.EXIT);
    }

    @Override
    public void setUser(@NonNull User user) {
        if (user.hasPassword) {
            header.showTitle(R.string.security_center_title_improve_your_security);

        } else {
            header.showTitle(R.string.security_center_title_backup_your_wallet);
        }
    }

    @Override
    public void showAbortDialog() {

        final MuunDialog muunDialog = new MuunDialog.Builder()
                .title(R.string.recovery_code_abort_title)
                .message(R.string.recovery_code_abort_body)
                .positiveButton(R.string.abort, presenter::onSetupAborted)
                .negativeButton(R.string.cancel, null)
                .build();

        showDialog(muunDialog);
    }

    @Override
    protected boolean blockScreenshots() {
        return true;
    }
}
