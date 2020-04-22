package io.muun.apollo.domain.errors


import io.muun.apollo.external.UserFacingErrorMessages

class EmailNotRegisteredError:
    UserFacingError(UserFacingErrorMessages.INSTANCE.emailNotRegistered())
