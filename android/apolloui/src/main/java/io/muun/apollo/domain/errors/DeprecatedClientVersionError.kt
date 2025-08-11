package io.muun.apollo.domain.errors


import io.muun.apollo.data.external.UserFacingErrorMessages

class DeprecatedClientVersionError : UserFacingError(
    UserFacingErrorMessages.INSTANCE.deprecatedClientVersion()
)
