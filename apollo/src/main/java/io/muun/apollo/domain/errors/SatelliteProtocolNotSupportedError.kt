package io.muun.apollo.domain.errors


import io.muun.apollo.external.UserFacingErrorMessages

class SatelliteProtocolNotSupportedError:
    UserFacingError(UserFacingErrorMessages.INSTANCE.satelliteProtocolNotSupported())
