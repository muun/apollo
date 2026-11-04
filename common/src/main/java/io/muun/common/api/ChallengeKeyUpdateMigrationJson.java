package io.muun.common.api;

import jakarta.validation.constraints.NotNull;
import javax.annotation.Nullable;

public class ChallengeKeyUpdateMigrationJson {

    @NotNull
    public String passwordKeySaltInHex;

    @Nullable
    public String recoveryCodeKeySaltInHex;

    @Nullable
    public String newEncrytpedMuunKey;

    /**
     * Json constructor.
     */
    public ChallengeKeyUpdateMigrationJson() {
    }

    /**
     * Data constructor.
     */
    public ChallengeKeyUpdateMigrationJson(String passwordKeySaltInHex,
                                           @Nullable String recoveryCodeKeySaltInHex,
                                           @Nullable String newEncrytpedMuunKey) {
        this.passwordKeySaltInHex = passwordKeySaltInHex;
        this.recoveryCodeKeySaltInHex = recoveryCodeKeySaltInHex;
        this.newEncrytpedMuunKey = newEncrytpedMuunKey;
    }
}
