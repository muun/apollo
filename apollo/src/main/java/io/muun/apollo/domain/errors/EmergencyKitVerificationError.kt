package io.muun.apollo.domain.errors

import io.muun.apollo.external.UserFacingErrorMessages


class EmergencyKitVerificationError:
    UserFacingError(UserFacingErrorMessages.INSTANCE.emergencyKitVerification())
