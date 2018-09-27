package io.muun.common.api;

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

    public SetupChallengeResponse() {
    }

    public SetupChallengeResponse(@Nullable String muunKey) {
        this.muunKey = muunKey;
    }
}
