package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.annotation.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateSessionRcOkJson {

    @Nullable   // Null if hasEmailSetup = true
    public KeySet keySet;

    public boolean hasEmailSetup;

    @Nullable
    public String obfuscatedEmail;

    @Nullable
    public String playIntegrityNonce;

    /**
     * Json constructor.
     */
    public CreateSessionRcOkJson() {
    }

    /**
     * Full constructor.
     */
    public CreateSessionRcOkJson(@Nullable KeySet keySet,
                                 boolean hasEmailSetup,
                                 @Nullable String obfuscatedEmail,
                                 @Nullable String playIntegrityNonce) {
        this.keySet = keySet;
        this.hasEmailSetup = hasEmailSetup;
        this.obfuscatedEmail = obfuscatedEmail;
        this.playIntegrityNonce = playIntegrityNonce;
    }
}
