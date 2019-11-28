package io.muun.apollo.domain.errors

import io.muun.apollo.external.UserFacingErrorMessages

class IncorrectPasswordError:
    UserFacingError(UserFacingErrorMessages.INSTANCE.incorrectPassword())
