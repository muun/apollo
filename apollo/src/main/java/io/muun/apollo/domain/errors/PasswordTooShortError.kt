package io.muun.apollo.domain.errors


import io.muun.apollo.data.external.UserFacingErrorMessages

class PasswordTooShortError:
    UserFacingError(UserFacingErrorMessages.INSTANCE.passwordTooShort())
