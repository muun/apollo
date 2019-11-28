package io.muun.apollo.domain.errors


import io.muun.apollo.external.UserFacingErrorMessages

class CountryNotSupportedError:
    UserFacingError(UserFacingErrorMessages.INSTANCE.countryNotSupported())
