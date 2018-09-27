package io.muun.common.model;

public class CreateSessionOk {

    private final boolean isExistingUser;
    private final boolean canUseRecoveryCode;

    /**
     * Constructor.
     */
    public CreateSessionOk(boolean isExistingUser,
                           boolean canUseRecoveryCode) {

        this.isExistingUser = isExistingUser;
        this.canUseRecoveryCode = canUseRecoveryCode;
    }

    public boolean isExistingUser() {
        return isExistingUser;
    }

    public boolean canUseRecoveryCode() {
        return canUseRecoveryCode;
    }
}
