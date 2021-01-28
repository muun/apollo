package io.muun.apollo.domain.errors


import io.muun.apollo.data.external.UserFacingErrorMessages

class EmailNotRegisteredError:
    UserFacingError(UserFacingErrorMessages.INSTANCE.emailNotRegistered())
