package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.hibernate.validator.constraints.NotEmpty;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PublicKeyJson {

    @NotEmpty
    public String key;

    @NotEmpty
    public String path;

    /**
     * Json constructor.
     */
    public PublicKeyJson() {
    }

    /**
     * Constructor.
     */
    public PublicKeyJson(String key, String path) {
        this.key = key;
        this.path = path;
    }
}
