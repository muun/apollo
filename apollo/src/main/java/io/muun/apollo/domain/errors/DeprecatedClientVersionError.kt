package io.muun.apollo.domain.errors


import io.muun.apollo.external.UserFacingErrorMessages

class DeprecatedClientVersionError:
    UserFacingError(UserFacingErrorMessages.INSTANCE.deprecatedClientVersion())
