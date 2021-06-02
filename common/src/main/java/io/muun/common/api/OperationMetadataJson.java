package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.annotation.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class OperationMetadataJson {

    @Nullable
    public String lnurlSender;

    @Nullable
    public String description;

    @Nullable
    public String invoice;

    public OperationMetadataJson() {
    }

    public OperationMetadataJson(@Nullable String description) {
        this.description = description;
    }
}
