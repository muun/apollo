package io.muun.apollo.presentation.ui.fragments.ek_success;

import io.muun.apollo.R;
import io.muun.apollo.presentation.ui.fragments.single_action.SingleActionFragment;

import android.view.View;

public class EmergencyKitSuccessFragment
        extends SingleActionFragment<EmergencyKitSuccessPresenter> {

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected void initializeUi(View view) {
        super.initializeUi(view);
        getParentActivity().getHeader().setVisibility(View.GONE);
    }

    @Override
    protected int getImageRes() {
        return R.drawable.tick;
    }

    @Override
    protected String getTitle() {
        return getString(R.string.ek_success_title);
    }

    @Override
    protected CharSequence getDescription() {
        return getString(R.string.ek_success_desc);
    }

    @Override
    protected int getActionLabelRes() {
        return R.string.ek_success_action;
    }

    @Override
    public boolean onBackPressed() {
        finishActivity();
        return true;
    }
}
