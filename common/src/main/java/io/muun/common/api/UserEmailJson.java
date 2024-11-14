package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserEmailJson {

    @NotNull
    public String email;

    /**
     * JSON constructor.
     */
    public UserEmailJson() {
    }

    /**
     * Constructor.
     */
    public UserEmailJson(String email) {
        this.email = email;
    }
}
