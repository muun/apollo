package io.muun.common.api;

import io.muun.common.Supports;
import io.muun.common.utils.Deprecated;
import io.muun.common.utils.Since;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.hibernate.validator.constraints.NotEmpty;

import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class KeySet {

    @NotEmpty
    public String encryptedPrivateKey;

    @Nullable // If user has not set up RC
    public String muunKey; // This is the encryptedMuunKey

    @Nullable
    @Since(apolloVersion = Supports.Fingerprint.APOLLO, falconVersion = Supports.Fingerprint.FALCON)
    public String muunKeyFingerprint;

    @Nullable
    @Deprecated(atApolloVersion = 46)
    public Map<String, byte[]> challengePublicKeys;

    @Since(apolloVersion = 40)
    @Nullable
    public List<ChallengeKeyJson> challengeKeys;

    /**
     * Json constructor.
     */
    public KeySet() {
    }

    /**
     * Houston constructor.
     */
    public KeySet(String encryptedPrivateKey,
                  @Nullable String muunKey,
                  @Nullable String muunKeyFingerprint,
                  @Nullable Map<String, byte[]> challengePublicKeys,
                  @Nullable List<ChallengeKeyJson> challengeKeys) {

        this.encryptedPrivateKey = encryptedPrivateKey;
        this.muunKey = muunKey;
        this.muunKeyFingerprint = muunKeyFingerprint;
        this.challengePublicKeys = challengePublicKeys;
        this.challengeKeys = challengeKeys;
    }
}
