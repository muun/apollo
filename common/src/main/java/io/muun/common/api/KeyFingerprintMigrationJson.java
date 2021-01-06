package io.muun.common.api;

import javax.validation.constraints.NotNull;

public class KeyFingerprintMigrationJson {

    @NotNull
    public String muunKeyFingerprint;

    /**
     * Json constructor.
     */
    public KeyFingerprintMigrationJson() {
    }

    /**
     * Data constructor.
     */
    public KeyFingerprintMigrationJson(@NotNull String muunKeyFingerprint) {
        this.muunKeyFingerprint = muunKeyFingerprint;
    }
}
