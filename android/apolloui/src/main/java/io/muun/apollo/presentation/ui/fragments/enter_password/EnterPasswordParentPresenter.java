package io.muun.apollo.presentation.ui.fragments.enter_password;

import io.muun.apollo.domain.action.base.ActionState;
import io.muun.apollo.presentation.ui.base.ParentPresenter;

import rx.Observable;

public interface EnterPasswordParentPresenter extends ParentPresenter {

    void submitEnterPassword(String password);

    Observable<ActionState<Void>> watchSubmitEnterPassword();

    boolean canUseRecoveryCodeToLogin();

    void useRecoveryCodeToLogin();

    void cancelEnterPassword();
}
