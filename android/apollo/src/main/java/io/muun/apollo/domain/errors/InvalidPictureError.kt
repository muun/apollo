package io.muun.apollo.domain.errors


import io.muun.apollo.data.external.UserFacingErrorMessages

class InvalidPictureError: UserFacingError {

    constructor():
        super(UserFacingErrorMessages.INSTANCE.invalidPicture())

    constructor(cause: Throwable):
        super(UserFacingErrorMessages.INSTANCE.invalidPicture(), cause)
}
