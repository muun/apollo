package io.muun.apollo.domain.errors.rc

import io.muun.apollo.data.external.UserFacingErrorMessages
import io.muun.apollo.domain.errors.UserFacingError

class InvalidRecoveryCodeV2Error: UserFacingError(UserFacingErrorMessages.INSTANCE.invalidRcV2())
