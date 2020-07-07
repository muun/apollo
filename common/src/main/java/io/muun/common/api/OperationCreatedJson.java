package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;


@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class OperationCreatedJson {

    @NotNull
    public OperationJson operation;

    @Nullable // null if the Operation was already fully signed
    public PartiallySignedTransactionJson partiallySignedTransaction;


    /**
     * Deprecated. This remains from the time when utxos were "locked" after 'newOperation'. Since
     * the utxo set "changed" back then, returning the NTS in the response was necessary. Since
     * this is no longer true now (utxo is updated and NTS returned after pushTransaction), this
     * property in this response is irrelevant. We only keeping for retro-compat with older clients.
     */
    @Deprecated
    @NotNull
    public NextTransactionSizeJson nextTransactionSize;

    @Nullable // null if the Operation has no change
    public MuunAddressJson changeAddress;

    /**
     * Json constructor.
     */
    public OperationCreatedJson() {
    }

    /**
     * Houston constructor.
     */
    public OperationCreatedJson(OperationJson operation,
                                PartiallySignedTransactionJson partiallySignedTransaction,
                                NextTransactionSizeJson nextTransactionSize,
                                @Nullable MuunAddressJson changeAddress) {

        this.operation = operation;
        this.partiallySignedTransaction = partiallySignedTransaction;
        this.nextTransactionSize = nextTransactionSize;
        this.changeAddress = changeAddress;
    }
}
