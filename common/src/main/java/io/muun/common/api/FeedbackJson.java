package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FeedbackJson {

    @NotNull
    public String content;

    /**
     * Json constructor.
     */
    public FeedbackJson() {
    }

    public FeedbackJson(String content) {
        this.content = content;
    }
}
