package io.muun.common.api;

import io.muun.common.Supports;
import io.muun.common.utils.Since;

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

    @NotNull
    @Since(apolloVersion = Supports.UserDebt.APOLLO, falconVersion = Supports.UserDebt.FALCON)
    public Long expectedDebtInSat;

    /**
     * Manual constructor.
     */
    public NextTransactionSizeJson(List<SizeForAmountJson> sizeProgression,
                                   @Nullable Long validAtOperationHid,
                                   Long expectedDebtInSat) {

        this.sizeProgression = sizeProgression;
        this.validAtOperationHid = validAtOperationHid;
        this.expectedDebtInSat = expectedDebtInSat;
    }

    /**
     * Json constructor.
     */
    public NextTransactionSizeJson() {
    }
}
