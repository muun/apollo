package io.muun.apollo.domain.errors


import io.muun.apollo.external.UserFacingErrorMessages

class PasswordsDontMatchError:
    UserFacingError(UserFacingErrorMessages.INSTANCE.passwordsDontMatch())
