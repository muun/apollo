package io.muun.apollo.domain.errors.rc

import io.muun.apollo.domain.errors.ErrorClassification
import io.muun.apollo.domain.errors.UserFacingError

class CredentialsDontMatchError : UserFacingError() {
    override val classification = ErrorClassification.EXPECTED
}