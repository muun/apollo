package io.muun.apollo.domain.errors;


import io.muun.apollo.external.UserFacingErrorMessages;

public class HardwareWalletAlreadyOwnedError extends UserFacingError {

    public HardwareWalletAlreadyOwnedError() {
        super(UserFacingErrorMessages.INSTANCE.hardwareWalletAlreadyOwned());
    }
}
