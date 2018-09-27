package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class NextTransactionSizeJson {

    @NotNull
    public List<SizeForAmountJson> sizeProgression;

    @Nullable
    public Long validAtOperationHid;

    /**
     * Manual constructor.
     */
    public NextTransactionSizeJson(List<SizeForAmountJson> sizeProgression,
                                   @Nullable Long validAtOperationHid) {

        this.sizeProgression = sizeProgression;
        this.validAtOperationHid = validAtOperationHid;
    }

    /**
     * Json constructor.
     */
    public NextTransactionSizeJson() {
    }
}
