package io.muun.apollo.domain.errors


import io.muun.apollo.data.external.UserFacingErrorMessages

class EmailAlreadyUsedError:
    UserFacingError(UserFacingErrorMessages.INSTANCE.emailAreadyUsed())
