package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.hibernate.validator.constraints.NotEmpty;

import javax.annotation.Nonnegative;
import javax.annotation.Nullable;
import javax.validation.constraints.Null;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SessionJson {

    @Null
    @Nullable
    public String uuid;

    @NotEmpty
    public String requestId;

    @Nullable // COMPAT: Apollo <22 sent phone instead of email, could not receive DEPRECATED msg
    public String email;

    @NotEmpty
    public String buildType;

    @Nonnegative
    public int version;

    @NotEmpty
    public String gcmRegistrationToken;

    /**
     * Json constructor.
     */
    public SessionJson() {
    }

    /**
     * Apollo constructor.
     */
    public SessionJson(String requestId,
                       String email,
                       String buildType,
                       int version,
                       String gcmRegistrationToken) {

        this.requestId = requestId;
        this.email = email;
        this.buildType = buildType;
        this.version = version;
        this.gcmRegistrationToken = gcmRegistrationToken;
    }

    /**
     * Houston constructor.
     */
    public SessionJson(String uuid,
                       String requestId,
                       String email,
                       String buildType,
                       int version,
                       String gcmRegistrationToken) {

        this.uuid = uuid;
        this.requestId = requestId;
        this.email = email;
        this.buildType = buildType;
        this.version = version;
        this.gcmRegistrationToken = gcmRegistrationToken;
    }
}
