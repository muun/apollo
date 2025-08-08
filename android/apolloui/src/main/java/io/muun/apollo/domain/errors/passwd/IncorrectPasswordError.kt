package io.muun.apollo.domain.errors.passwd

import io.muun.apollo.data.external.UserFacingErrorMessages
import io.muun.apollo.domain.errors.UserFacingError

class IncorrectPasswordError : UserFacingError(UserFacingErrorMessages.INSTANCE.incorrectPassword())
