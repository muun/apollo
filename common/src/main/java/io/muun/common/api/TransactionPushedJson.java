package io.muun.common.api;

import io.muun.common.api.error.BroadcastErrorJson;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotNull;
import javax.annotation.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransactionPushedJson {

    @Nullable
    public String hex; // COMPAT: lousy modeling, but this field is expected by Apollo < 35

    @Nullable // NTS is absent when the utxo set hasn't changed
    public NextTransactionSizeJson nextTransactionSize;

    @NotNull
    public OperationJson updatedOperation;

    @Nullable // Null if the broadcast didn't failed.
    public BroadcastErrorJson broadcastErrorCode;

    @NotNull
    public FeeBumpFunctionsJson feeBumpFunctions;

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
            @Nullable NextTransactionSizeJson nextTransactionSize,
            OperationJson operation,
            @Nullable BroadcastErrorJson broadcastErrorCode,
            FeeBumpFunctionsJson feeBumpFunctions
    ) {

        this.hex = hex;
        this.nextTransactionSize = nextTransactionSize;
        this.updatedOperation = operation;
        this.broadcastErrorCode = broadcastErrorCode;
        this.feeBumpFunctions = feeBumpFunctions;
    }
}
