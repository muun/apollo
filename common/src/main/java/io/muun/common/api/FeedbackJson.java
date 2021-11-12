package io.muun.common.api;

import io.muun.common.Supports;
import io.muun.common.utils.Since;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FeedbackJson {

    public enum Type {
        SUPPORT,
        CLOUD_REQUEST
    }

    @NotNull
    public String content;

    @Since(apolloVersion = Supports.Taproot.APOLLO, falconVersion = Supports.Taproot.FALCON)
    @Nullable
    public Type type;

    /**
     * Json constructor.
     */
    public FeedbackJson() {
    }

    public FeedbackJson(String content, @NotNull Type type) {
        this.content = content;
        this.type = type;
    }
}
