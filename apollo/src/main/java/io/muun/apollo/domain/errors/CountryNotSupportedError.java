package io.muun.apollo.domain.errors;


public class CountryNotSupportedError extends UserFacingError {

    public CountryNotSupportedError() {
        super("Sorry! Muun is not yet available in that country");
    }
}
