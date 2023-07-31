package io.muun.common.api;

import io.muun.common.Supports;
import io.muun.common.utils.Since;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SetupChallengeResponse {

    @Nullable
    @JsonProperty("muunKey")
    public String muunKey;

    @Nullable
    @Since(apolloVersion = Supports.Fingerprint.APOLLO, falconVersion = Supports.Fingerprint.FALCON)
    public String muunKeyFingerprint;

    /**
     * Json constructor.
     */
    public SetupChallengeResponse() {
    }
}
