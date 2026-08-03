package io.muun.apollo.domain.errors.rc

import io.muun.apollo.domain.errors.ErrorClassification
import io.muun.apollo.domain.errors.UserFacingError

class StaleChallengeKeyError : UserFacingError() {
    override val classification = ErrorClassification.EXPECTED
}