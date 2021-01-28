package io.muun.apollo.presentation.ui.settings.edit_password;

import io.muun.apollo.domain.model.ChangePasswordStep;
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter;
import io.muun.apollo.presentation.ui.base.SingleFragmentView;
import io.muun.apollo.presentation.ui.settings.EmailWaitFragment;
import io.muun.apollo.presentation.ui.settings.OldPasswordFragment;
import io.muun.apollo.presentation.ui.settings.RecoveryCodeFragment;

import androidx.fragment.app.Fragment;

public class BaseEditPasswordPresenter<T extends SingleFragmentView>
        extends SingleFragmentPresenter<T, EditPasswordPresenter> {

    /**
     * Change the visible fragment, allowing to come back to current fragment.
     */
    public void navigateToStep(ChangePasswordStep step) {
        navigateToStep(step, true);
    }

    /**
     * Change the visible fragment.
     */
    public void navigateToStep(ChangePasswordStep step, boolean canComeBackToCurrent) {
        final Fragment fragment = createStepFragment(step);

        view.replaceFragment(fragment, canComeBackToCurrent);
    }

    private Fragment createStepFragment(ChangePasswordStep step) {
        switch (step) {
            case START:
                return new StartPasswordChangeFragment();

            case EXISTING_PASSWORD:
                return new OldPasswordFragment();

            case EXISTING_RECOVERY_CODE:
                return new RecoveryCodeFragment();

            case WAIT_FOR_EMAIL:
                return new EmailWaitFragment();

            case NEW_PASSWORD:
                return new ChangePasswordFragment();

            default:
                return new StartPasswordChangeFragment(); // ?
        }
    }
}
