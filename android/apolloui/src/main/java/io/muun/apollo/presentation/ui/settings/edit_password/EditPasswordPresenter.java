package io.muun.apollo.presentation.ui.settings.edit_password;

import io.muun.apollo.presentation.ui.base.BasePresenter;
import io.muun.apollo.presentation.ui.base.di.PerActivity;
import io.muun.apollo.presentation.ui.settings.edit_password.success.EditPasswordSuccessFragment;

import icepick.State;

import javax.inject.Inject;

@PerActivity
public class EditPasswordPresenter extends BasePresenter<EditPasswordView> {

    @State
    public String currentUuid;

    /**
     * Creates a presenter.
     */
    @Inject
    public EditPasswordPresenter() {
    }

    /**
     * Call when password change has finished.
     */
    public void onChangeSuccessful() {
        view.replaceFragment(new EditPasswordSuccessFragment(), false);
    }
}
