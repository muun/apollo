package io.muun.apollo.domain.errors


import io.muun.apollo.external.UserFacingErrorMessages

class HardwareWalletAlreadyOwnedError:
    UserFacingError(UserFacingErrorMessages.INSTANCE.hardwareWalletAlreadyOwned())
