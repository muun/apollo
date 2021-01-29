package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PreimageJson {

    @NotNull
    public String hex;

    /**
     * Json constructor.
     */
    public PreimageJson() {
    }

    /**
     * Manual constructor.
     */
    public PreimageJson(String hex) {
        this.hex = hex;
    }
}
