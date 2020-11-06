package io.muun.apollo.domain.errors


import io.muun.apollo.data.external.UserFacingErrorMessages

class CountryNotSupportedError:
    UserFacingError(UserFacingErrorMessages.INSTANCE.countryNotSupported())
