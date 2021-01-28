package io.muun.apollo.domain.model;

import io.muun.common.model.PhoneNumber;

import javax.annotation.Nullable;

public class UserPhoneNumber extends PhoneNumber {

    private final boolean isVerified;

    /**
     * Constructor.
     * @throws IllegalArgumentException when the phoneNumber is not valid.
     */
    public UserPhoneNumber(String e164PhoneNumber, boolean isVerified) {
        super(e164PhoneNumber);
        this.isVerified = isVerified;
    }

    /**
     * Constructor.
     * @throws IllegalArgumentException when the phoneNumber is not valid.
     */
    public UserPhoneNumber(String phoneNumber, @Nullable String regionCode, boolean isVerified)
            throws IllegalArgumentException {

        super(phoneNumber, regionCode);
        this.isVerified = isVerified;
    }

    public boolean isVerified() {
        return isVerified;
    }
}
