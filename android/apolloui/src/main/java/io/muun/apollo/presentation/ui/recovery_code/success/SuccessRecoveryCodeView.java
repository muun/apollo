package io.muun.apollo.presentation.ui.recovery_code.success;

import io.muun.apollo.domain.model.User;
import io.muun.apollo.presentation.ui.base.BaseView;

interface SuccessRecoveryCodeView extends BaseView {

    void setTexts(User user);
}
