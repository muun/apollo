package io.muun.apollo.domain.errors


import io.muun.apollo.external.UserFacingErrorMessages

class FcmTokenNotAvailableError:
    UserFacingError(UserFacingErrorMessages.INSTANCE.fcmTokenNotAvailable())
