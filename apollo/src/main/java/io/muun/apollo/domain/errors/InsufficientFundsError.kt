package io.muun.apollo.domain.errors


import io.muun.apollo.external.UserFacingErrorMessages

class InsufficientFundsError:
    UserFacingError(UserFacingErrorMessages.INSTANCE.insufficientFunds())
