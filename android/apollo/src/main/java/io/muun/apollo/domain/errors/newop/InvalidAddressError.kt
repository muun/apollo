package io.muun.apollo.domain.errors.newop


import io.muun.apollo.data.external.UserFacingErrorMessages
import io.muun.apollo.domain.errors.UserFacingError

class InvalidAddressError : UserFacingError(UserFacingErrorMessages.INSTANCE.invalidAddress())
