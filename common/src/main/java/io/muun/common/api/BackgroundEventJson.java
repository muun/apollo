package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class BackgroundEventJson {

    @NotEmpty
    public Long beginTimestampInMillis;

    @NotNull
    public Long durationInMillis;


    /**
     * Json constructor.
     */
    public BackgroundEventJson() {
    }

    /**
     * Manual constructor.
     */
    public BackgroundEventJson(Long beginTimestampInMillis, Long durationInMillis) {
        this.beginTimestampInMillis = beginTimestampInMillis;
        this.durationInMillis = durationInMillis;
    }
}
