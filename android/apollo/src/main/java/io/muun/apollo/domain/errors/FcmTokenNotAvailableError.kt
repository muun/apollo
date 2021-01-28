package io.muun.apollo.domain.errors


import io.muun.apollo.data.external.UserFacingErrorMessages

class FcmTokenNotAvailableError:
    UserFacingError(UserFacingErrorMessages.INSTANCE.fcmTokenNotAvailable())
