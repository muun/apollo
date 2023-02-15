package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.annotation.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlayIntegrityTokenJson {

    /**
     * The token returned by the Play Integrity API, if any. If null, set errorCode.
     */
    @Nullable
    public String token;

    /**
     * The error code returned by the Play Integrity API, if any. Should be present if token is not.
     */
    @Nullable
    public String errorCode;

    /**
     * JSON constructor.
     */
    public PlayIntegrityTokenJson() {
    }

    /**
     * Apollo constructor.
     */
    public PlayIntegrityTokenJson(@Nullable String token, @Nullable String errorCode) {
        this.token = token;
        this.errorCode = errorCode;
    }
}
