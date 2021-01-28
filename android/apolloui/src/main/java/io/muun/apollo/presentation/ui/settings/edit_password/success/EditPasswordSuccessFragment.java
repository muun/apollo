package io.muun.apollo.presentation.ui.settings.edit_password.success;

import io.muun.apollo.R;
import io.muun.apollo.presentation.ui.base.BaseView;
import io.muun.apollo.presentation.ui.fragments.single_action.SingleActionFragment;
import io.muun.apollo.presentation.ui.fragments.single_action.SingleActionPresenter;
import io.muun.apollo.presentation.ui.settings.edit_password.EditPasswordPresenter;

import android.view.View;

public class EditPasswordSuccessFragment
        extends SingleActionFragment<SingleActionPresenter<BaseView, EditPasswordPresenter>> {

    @Override
    protected void inject() {
        getComponent().inject(this);
    }

    @Override
    protected int getImageRes() {
        return R.drawable.tick;
    }

    @Override
    protected String getTitle() {
        return getString(R.string.change_password_success_message);
    }

    @Override
    protected int getActionLabelRes() {
        return R.string.change_password_success_action;
    }

    @Override
    protected void initializeUi(View view) {
        getParentActivity().getHeader().setVisibility(View.GONE);
        super.initializeUi(view);
    }

    @Override
    public boolean onBackPressed() {
        finishActivity();
        return true;
    }
}
