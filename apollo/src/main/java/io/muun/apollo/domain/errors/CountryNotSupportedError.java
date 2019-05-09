package io.muun.apollo.domain.errors;


import io.muun.apollo.external.UserFacingErrorMessages;

public class CountryNotSupportedError extends UserFacingError {

    public CountryNotSupportedError() {
        super(UserFacingErrorMessages.INSTANCE.countryNotSupported());
    }
}
