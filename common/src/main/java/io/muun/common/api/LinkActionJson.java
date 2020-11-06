package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class LinkActionJson {

    @NotNull
    public String uuid;

    /**
     * JSON constructor.
     */
    public LinkActionJson() {
    }

    /**
     * Code constructor.
     */
    public LinkActionJson(String uuid) {
        this.uuid = uuid;
    }
}
