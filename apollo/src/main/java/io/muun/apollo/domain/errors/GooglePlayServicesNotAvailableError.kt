package io.muun.apollo.domain.errors


import io.muun.apollo.data.external.UserFacingErrorMessages

class GooglePlayServicesNotAvailableError:
    UserFacingError(UserFacingErrorMessages.INSTANCE.googlePlayServicesNotAvailable())
