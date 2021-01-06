package io.muun.common.api;

import io.muun.common.api.error.BroadcastErrorJson;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransactionPushedJson {

    @Nullable
    public String hex; // COMPAT: lousy modeling, but this field is expected by Apollo < 35

    @NotNull
    public NextTransactionSizeJson nextTransactionSize;

    @NotNull
    public OperationJson updatedOperation;

    @Nullable // Null if the broadcast didn't failed.
    public BroadcastErrorJson broadcastErrorCode;

    /**
     * Json constructor.
     */
    public TransactionPushedJson() {
    }

    /**
     * Houston constructor.
     */
    public TransactionPushedJson(
            @Nullable String hex,
            NextTransactionSizeJson nextTransactionSize,
            OperationJson operation,
            @Nullable BroadcastErrorJson broadcastErrorCode) {

        this.hex = hex;
        this.nextTransactionSize = nextTransactionSize;
        this.updatedOperation = operation;
        this.broadcastErrorCode = broadcastErrorCode;
    }
}
