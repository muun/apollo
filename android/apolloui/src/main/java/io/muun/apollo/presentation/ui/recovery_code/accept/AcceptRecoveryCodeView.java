package io.muun.apollo.presentation.ui.recovery_code.accept;


import io.muun.apollo.domain.model.user.User;
import io.muun.apollo.presentation.ui.base.SingleFragmentView;

public interface AcceptRecoveryCodeView extends SingleFragmentView {

    /**
     * Set this view's texts based on the current state of the user.
     */
    void setTexts(User user);

    /**
     * Set this view's state to loading.
     */
    void setLoading(boolean isLoading);
}
