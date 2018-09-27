package io.muun.common.api.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class JwtJson {

    public String jwt;

    /**
     * Json constructor.
     */
    public JwtJson() {
    }

    /**
     * Service constructor.
     */
    public JwtJson(String jwt) {
        this.jwt = jwt;
    }
}
