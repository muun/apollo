package io.muun.apollo.domain.errors


import io.muun.apollo.data.external.UserFacingErrorMessages

class PasswordsDontMatchError:
    UserFacingError(UserFacingErrorMessages.INSTANCE.passwordsDontMatch())
