package io.muun.common.model;

import io.muun.common.Optional;
import io.muun.common.api.KeySet;

import javax.annotation.Nullable;

public class CreateSessionRcOk {

    private final Optional<KeySet> keySet; // Empty if hasEmailSetup = true
    private final boolean hasEmailSetup;
    private final Optional<String> obfuscatedEmail;  // Empty if if hasEmailSetup = false

    /**
     * Constructor.
     */
    public CreateSessionRcOk(@Nullable KeySet keySet,
                             boolean hasEmailSetup,
                             @Nullable String obfuscatedEmail) {

        this.keySet = Optional.ofNullable(keySet);
        this.hasEmailSetup = hasEmailSetup;
        this.obfuscatedEmail = Optional.ofNullable(obfuscatedEmail);
    }

    public Optional<KeySet> getKeySet() {
        return keySet;
    }

    /**
     * Getter for hasEmailSetup.
     */
    public boolean hasEmailSetup() {
        return hasEmailSetup;
    }

    public Optional<String> getObfuscatedEmail() {
        return obfuscatedEmail;
    }
}
