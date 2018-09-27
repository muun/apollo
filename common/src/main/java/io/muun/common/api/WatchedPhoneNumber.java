package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.hibernate.validator.constraints.NotEmpty;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class WatchedPhoneNumber {

    @NotEmpty
    public String hash;

    /**
     * Json constructor.
     */
    public WatchedPhoneNumber() {
    }

    /**
     * Apollo constructor.
     */
    public WatchedPhoneNumber(String hash) {
        this.hash = hash;
    }
}
