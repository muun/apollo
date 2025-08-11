package io.muun.apollo.domain.errors.ek

import io.muun.apollo.data.external.UserFacingErrorMessages
import io.muun.apollo.domain.errors.UserFacingError

class SaveEkToDiskError(cause: Throwable) : UserFacingError(
    UserFacingErrorMessages.INSTANCE.saveEkToDisk(),
    cause
)