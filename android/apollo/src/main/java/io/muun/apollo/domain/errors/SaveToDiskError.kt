package io.muun.apollo.domain.errors

import io.muun.apollo.data.external.UserFacingErrorMessages

class SaveToDiskError(cause: Throwable) :
    UserFacingError(UserFacingErrorMessages.INSTANCE.saveEkToDisk(), cause)