package io.muun.apollo.presentation.ui.setup_p2p;

import io.muun.apollo.R;
import io.muun.apollo.databinding.SetupP2pActivityBinding;
import io.muun.apollo.presentation.ui.base.BaseView;
import io.muun.apollo.presentation.ui.base.SingleFragment;
import io.muun.apollo.presentation.ui.base.SingleFragmentActivity;
import io.muun.apollo.presentation.ui.view.MuunHeader;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import androidx.viewbinding.ViewBinding;
import kotlin.jvm.functions.Function1;

import jakarta.validation.constraints.NotNull;

public class SetupP2PActivity extends SingleFragmentActivity<SetupP2PPresenter>
        implements BaseView {

    /**
     * Creates an intent to launch this activity.
     */
    public static Intent getStartActivityIntent(@NotNull Context context) {
        return new Intent(context, SetupP2PActivity.class);
    }

    private SetupP2pActivityBinding binding() {
        return (SetupP2pActivityBinding) getBinding();
    }

    @Override
    protected Function1<LayoutInflater, ViewBinding> bindingInflater() {
        return SetupP2pActivityBinding::inflate;
    }

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected int getFragmentsContainer() {
        return R.id.setup_p2p_frame_container;
    }

    @Override
    public MuunHeader getHeader() {
        return binding().setupP2pHeader;
    }

    @Override
    protected SingleFragment getInitialFragment() {
        return presenter.getInitialStep();
    }
}
