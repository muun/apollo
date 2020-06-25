package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.hibernate.validator.constraints.NotEmpty;

import javax.annotation.Nonnegative;
import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClientJson {

    @NotNull
    public ClientTypeJson type;

    @NotEmpty
    public String buildType;

    @Nonnegative
    public int version;

    /**
     * Json constructor.
     */
    public ClientJson() {
    }

    /**
     * Code constructor.
     */
    public ClientJson(ClientTypeJson type, String buildType, int version) {
        this.type = type;
        this.buildType = buildType;
        this.version = version;
    }
}
