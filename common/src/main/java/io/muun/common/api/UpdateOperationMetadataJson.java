package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateOperationMetadataJson {

    public String receiverMetadata;

    /**
     * Json constructor.
     */
    public UpdateOperationMetadataJson() {
    }

    /**
     * Client constructor.
     */
    public UpdateOperationMetadataJson(@NotNull String receiverMetadata) {
        this.receiverMetadata = receiverMetadata;
    }

}
