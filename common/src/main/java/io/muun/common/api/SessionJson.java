package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.hibernate.validator.constraints.NotEmpty;

import javax.annotation.Nonnegative;
import javax.annotation.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SessionJson {

    @Nullable // COMPAT: Apollo <22 sent phone instead of email, could not receive DEPRECATED msg
    public String email;

    @NotEmpty
    public String buildType;

    @Nonnegative
    public int version; // this is the clientVersion, not a session version.

    @NotEmpty
    public String gcmRegistrationToken;

    @Nullable // This is sent only by newer clients. Older Apollos send it empty.
    public ClientTypeJson clientType;

    /**
     * Json constructor.
     */
    public SessionJson() {
    }

    /**
     * Apollo constructor.
     */
    public SessionJson(String email,
                       String buildType,
                       int version,
                       String gcmRegistrationToken,
                       ClientTypeJson clientType) {

        this.email = email;
        this.buildType = buildType;
        this.version = version;
        this.gcmRegistrationToken = gcmRegistrationToken;
        this.clientType = clientType;
    }
}
