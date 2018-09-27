package io.muun.common.api;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SignatureJson {

    @NotNull
    public String hex;

    /**
     * Json constructor.
     */
    public SignatureJson() {
    }

    /**
     * Manual constructor.
     */
    public SignatureJson(String hex) {
        this.hex = hex;
    }
}
