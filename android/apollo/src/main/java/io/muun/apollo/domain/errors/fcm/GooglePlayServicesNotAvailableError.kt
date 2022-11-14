package io.muun.apollo.domain.errors.fcm


import io.muun.apollo.data.external.UserFacingErrorMessages
import io.muun.apollo.domain.errors.UserFacingError

class GooglePlayServicesNotAvailableError:
    UserFacingError(UserFacingErrorMessages.INSTANCE.googlePlayServicesNotAvailable())
