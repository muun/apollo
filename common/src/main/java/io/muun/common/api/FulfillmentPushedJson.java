package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FulfillmentPushedJson {

    @NotNull
    public NextTransactionSizeJson nextTransactionSize;

    @NotNull
    public FeeBumpFunctionsJson feeBumpFunctions;

    /**
     * Json constructor.
     */
    public FulfillmentPushedJson() {
    }

    public FulfillmentPushedJson(
            NextTransactionSizeJson nextTransactionSize,
            FeeBumpFunctionsJson feeBumpFunctions
    ) {
        this.nextTransactionSize = nextTransactionSize;
        this.feeBumpFunctions = feeBumpFunctions;
    }
}
