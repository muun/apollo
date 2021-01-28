package io.muun.apollo.presentation.ui.recovery_code.priming;

import io.muun.apollo.domain.model.User;
import io.muun.apollo.presentation.ui.base.SingleFragmentView;

public interface PrimingRecoveryCodeView extends SingleFragmentView {

    void setTexts(User user);
}
