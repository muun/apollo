package io.muun.apollo.domain.errors

import io.muun.apollo.data.external.UserFacingErrorMessages

class InvalidRecoveryCodeV2Error: UserFacingError(UserFacingErrorMessages.INSTANCE.invalidRcV2())
