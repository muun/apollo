package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class BackgroundEventJson {

    //Fix for Apollo v52.7(aka 1207)
    @JsonAlias("beginTimeInMillis")
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
