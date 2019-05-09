package io.muun.apollo.domain.errors;


import io.muun.apollo.external.UserFacingErrorMessages;

public class SatelliteAlreadyPairedError extends UserFacingError {

    public SatelliteAlreadyPairedError() {
        super(UserFacingErrorMessages.INSTANCE.satelliteAlreadyPaired());
    }
}
