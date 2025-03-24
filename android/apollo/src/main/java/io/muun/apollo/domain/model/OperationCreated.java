package io.muun.apollo.domain.model;


import io.muun.apollo.domain.model.tx.PartiallySignedTransaction;
import io.muun.common.crypto.hd.MuunAddress;

import java.util.List;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

public class OperationCreated {

    @NotNull
    public final OperationWithMetadata operation;

    @NotNull
    public final PartiallySignedTransaction partiallySignedTransaction;

    @NotNull
    public final NextTransactionSize nextTransactionSize;

    @Nullable // null if the Operation has no change
    public final MuunAddress changeAddress;

    public final List<PartiallySignedTransaction> alternativeTransactions;

    /**
     * Constructor.
     */
    public OperationCreated(OperationWithMetadata operation,
                            PartiallySignedTransaction partiallySignedTransaction,
                            NextTransactionSize nextTransactionSize,
                            @Nullable MuunAddress changeAddress,
                            List<PartiallySignedTransaction> alternativeTransactions
    ) {

        this.operation = operation;
        this.partiallySignedTransaction = partiallySignedTransaction;
        this.nextTransactionSize = nextTransactionSize;
        this.changeAddress = changeAddress;
        this.alternativeTransactions = alternativeTransactions;
    }
}
