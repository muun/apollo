package io.muun.apollo.domain.errors

import io.muun.apollo.data.external.UserFacingErrorMessages

class IncorrectPasswordError:
    UserFacingError(UserFacingErrorMessages.INSTANCE.incorrectPassword())
