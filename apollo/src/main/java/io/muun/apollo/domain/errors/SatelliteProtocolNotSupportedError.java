package io.muun.apollo.domain.errors;


import io.muun.apollo.external.UserFacingErrorMessages;

public class SatelliteProtocolNotSupportedError extends UserFacingError {

    public SatelliteProtocolNotSupportedError() {
        super(UserFacingErrorMessages.INSTANCE.satelliteProtocolNotSupported());
    }
}
