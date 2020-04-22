package io.muun.apollo.domain.errors


import io.muun.apollo.external.UserFacingErrorMessages

class EmailAlreadyUsedError:
    UserFacingError(UserFacingErrorMessages.INSTANCE.emailAreadyUsed())
