package io.muun.apollo.presentation.ui.recovery_code.priming;

import io.muun.apollo.domain.model.user.User;
import io.muun.apollo.presentation.ui.base.SingleFragmentView;

public interface PrimingRecoveryCodeView extends SingleFragmentView {

    /**
     * Set this view's texts based on the current state of the user.
     */
    void setTexts(User user);
}
