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
     * The "name" (aka string representation) of the error code (int( returned by the Play Integrity
     * API, if any. Should be present if token is not.
     */
    @Nullable
    public String errorCode;

    /**
     * The error cause for errors where an errorCode couldn't be parsed successfully.
     */
    @Nullable
    public String errorCause;

    /**
     * JSON constructor.
     */
    public PlayIntegrityTokenJson() {
    }

    /**
     * Apollo constructor.
     */
    public PlayIntegrityTokenJson(
            @Nullable String token,
            @Nullable String errorCode,
            @Nullable String errorCause
    ) {
        this.token = token;
        this.errorCode = errorCode;
        this.errorCause = errorCause;
    }
}
