package io.muun.apollo.presentation.ui.fragments.phone_number;


import io.muun.apollo.domain.errors.UserFacingError;
import io.muun.apollo.presentation.ui.base.SingleFragmentView;

public interface PhoneNumberView extends SingleFragmentView {
    void setLoading(boolean isLoading);

    void setPhoneNumberError(UserFacingError error);
}