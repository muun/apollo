package io.muun.apollo.presentation.ui.recovery_code.show;


import io.muun.apollo.domain.libwallet.RecoveryCodeV2;
import io.muun.apollo.presentation.ui.base.SingleFragmentView;

public interface ShowRecoveryCodeView extends SingleFragmentView {

    void setRecoveryCode(RecoveryCodeV2 recoveryCode);
}
