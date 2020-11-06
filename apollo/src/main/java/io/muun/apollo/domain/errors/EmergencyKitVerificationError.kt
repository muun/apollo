package io.muun.apollo.domain.errors

import io.muun.apollo.data.external.UserFacingErrorMessages


class EmergencyKitVerificationError:
    UserFacingError(UserFacingErrorMessages.INSTANCE.emergencyKitVerification())
