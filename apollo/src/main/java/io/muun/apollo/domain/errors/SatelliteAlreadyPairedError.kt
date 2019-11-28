package io.muun.apollo.domain.errors


import io.muun.apollo.external.UserFacingErrorMessages

class SatelliteAlreadyPairedError:
    UserFacingError(UserFacingErrorMessages.INSTANCE.satelliteAlreadyPaired())
