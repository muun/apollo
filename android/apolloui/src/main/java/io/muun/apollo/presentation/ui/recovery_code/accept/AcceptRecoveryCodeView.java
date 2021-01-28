package io.muun.apollo.presentation.ui.recovery_code.accept;


import io.muun.apollo.domain.model.User;
import io.muun.apollo.presentation.ui.base.SingleFragmentView;

public interface AcceptRecoveryCodeView extends SingleFragmentView {

    void setTexts(User user);

    void setLoading(boolean isLoading);
}
