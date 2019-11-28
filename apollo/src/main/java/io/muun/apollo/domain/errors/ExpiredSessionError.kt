package io.muun.apollo.domain.errors


import io.muun.apollo.external.UserFacingErrorMessages

class ExpiredSessionError:
    UserFacingError(UserFacingErrorMessages.INSTANCE.expiredSession())
