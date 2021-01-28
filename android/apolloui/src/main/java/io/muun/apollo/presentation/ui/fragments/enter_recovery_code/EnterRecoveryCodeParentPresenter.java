package io.muun.apollo.presentation.ui.fragments.enter_recovery_code;

import io.muun.apollo.domain.action.base.ActionState;
import io.muun.apollo.presentation.ui.base.ParentPresenter;

import rx.Observable;

public interface EnterRecoveryCodeParentPresenter extends ParentPresenter {

    void submitEnterRecoveryCode(String recoveryCode);

    Observable<ActionState<Void>> watchSubmitEnterRecoveryCode();

    void cancelEnterRecoveryCode();
}
