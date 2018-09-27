package io.muun.apollo.domain.action;

import io.muun.apollo.data.os.TelephonyInfoProvider;
import io.muun.apollo.domain.errors.InvalidPhoneNumberError;
import io.muun.common.model.PhoneNumber;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class PhoneActions {

    private final TelephonyInfoProvider telephony;

    @Inject
    public PhoneActions(TelephonyInfoProvider telephony) {
        this.telephony = telephony;
    }

    /**
     * Creates a phone number model.
     */
    public PhoneNumber buildPhoneNumber(String number) throws InvalidPhoneNumberError {
        try {
            return new PhoneNumber(number);

        } catch (IllegalArgumentException ex) {
            throw new InvalidPhoneNumberError(ex);
        }
    }
}
