package io.muun.apollo.presentation.ui.fragments.phone_number;

import io.muun.apollo.domain.action.base.ActionState;
import io.muun.apollo.domain.model.user.UserPhoneNumber;
import io.muun.apollo.presentation.ui.base.ParentPresenter;
import io.muun.common.model.PhoneNumber;

import rx.Observable;

public interface PhoneNumberParentPresenter extends ParentPresenter {

    /**
     * Submit a phone number as part of the P2P setup.
     */
    void submitPhoneNumber(PhoneNumber phoneNumber);

    /**
     * Get an Observable for the SubmitPhoneNumber async action.
     */
    Observable<ActionState<UserPhoneNumber>> watchSubmitPhoneNumber();

}
