package io.muun.apollo.domain.errors


import io.muun.apollo.data.external.UserFacingErrorMessages

class ExpiredSessionError : UserFacingError(
    UserFacingErrorMessages.INSTANCE.expiredSession()
)
