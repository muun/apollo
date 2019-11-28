package io.muun.apollo.domain.errors


import io.muun.apollo.external.UserFacingErrorMessages

class EmailAreadyUsedError:
    UserFacingError(UserFacingErrorMessages.INSTANCE.emailAreadyUsed())
