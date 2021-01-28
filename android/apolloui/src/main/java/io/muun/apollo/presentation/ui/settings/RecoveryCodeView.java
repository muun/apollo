package io.muun.apollo.presentation.ui.settings;

import io.muun.apollo.domain.errors.UserFacingError;
import io.muun.apollo.presentation.ui.base.SingleFragmentView;

public interface RecoveryCodeView extends SingleFragmentView {

    void setRecoveryCodeError(UserFacingError error);

    void setConfirmEnabled(boolean enabled);
}
