package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.hibernate.validator.constraints.NotEmpty;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class RawTransaction {

    @NotEmpty
    public String hex;

    /**
     * Json constructor.
     */
    public RawTransaction() {
    }

    /**
     * Apollo constructor.
     */
    public RawTransaction(String hex) {
        this.hex = hex;
    }
}
