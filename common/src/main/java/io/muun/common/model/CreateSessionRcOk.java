package io.muun.common.model;

import io.muun.common.Optional;
import io.muun.common.api.KeySet;

import javax.annotation.Nullable;

public class CreateSessionRcOk {

    private final Optional<KeySet> keySet; // Empty if hasEmailSetup = true
    private final boolean hasEmailSetup;
    private final Optional<String> ofuscatedEmail;  // Empty if if hasEmailSetup = false

    /**
     * Constructor.
     */
    public CreateSessionRcOk(@Nullable KeySet keySet,
                             boolean hasEmailSetup,
                             @Nullable String ofuscatedEmail) {

        this.keySet = Optional.ofNullable(keySet);
        this.hasEmailSetup = hasEmailSetup;
        this.ofuscatedEmail = Optional.ofNullable(ofuscatedEmail);
    }

    public Optional<KeySet> getKeySet() {
        return keySet;
    }

    public boolean hasEmailSetup() {
        return hasEmailSetup;
    }

    public Optional<String> getOfuscatedEmail() {
        return ofuscatedEmail;
    }
}
