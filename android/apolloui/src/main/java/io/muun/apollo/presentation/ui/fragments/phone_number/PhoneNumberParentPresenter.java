package io.muun.apollo.presentation.ui.fragments.phone_number;

import io.muun.apollo.domain.action.base.ActionState;
import io.muun.apollo.domain.model.UserPhoneNumber;
import io.muun.apollo.presentation.ui.base.ParentPresenter;
import io.muun.common.model.PhoneNumber;

import rx.Observable;

public interface PhoneNumberParentPresenter extends ParentPresenter {

    void submitPhoneNumber(PhoneNumber phoneNumber);

    Observable<ActionState<UserPhoneNumber>> watchSubmitPhoneNumber();

}
