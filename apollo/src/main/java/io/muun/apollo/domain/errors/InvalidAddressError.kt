package io.muun.apollo.domain.errors


import io.muun.apollo.external.UserFacingErrorMessages

class InvalidAddressError:
    UserFacingError(UserFacingErrorMessages.INSTANCE.invalidAddress())
