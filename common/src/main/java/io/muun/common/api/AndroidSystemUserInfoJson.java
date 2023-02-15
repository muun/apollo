package io.muun.common.api;

import io.muun.common.utils.Since;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AndroidSystemUserInfoJson {

    @Since(apolloVersion = 1006) // Apollo only field
    public Long creationTimestampInMilliseconds;

    @Since(apolloVersion = 1006) // Apollo only field
    public Boolean isSystemUser;


    /**
     * Json constructor.
     */
    @SuppressWarnings("unused") // Jackson requires it
    public AndroidSystemUserInfoJson() {
    }

    /**
     * Code constructor.
     */
    public AndroidSystemUserInfoJson(Long creationTimestampInMilliseconds, Boolean isSystemUser) {
        this.creationTimestampInMilliseconds = creationTimestampInMilliseconds;
        this.isSystemUser = isSystemUser;
    }
}
