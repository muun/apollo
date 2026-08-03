package io.muun.apollo.domain.errors;

import org.jetbrains.annotations.NotNull;

public class DebtNegativeError extends MuunError {

    @NotNull
    @Override
    public ErrorClassification getClassification() {
        return ErrorClassification.UNEXPECTED;
    }

    public DebtNegativeError(
            final Long ntsValidAtOperationHid,
            final long utxoBalance,
            final long expectedDebtInSat
    ) {
        getMetadata().put("validAtOperationHid", ntsValidAtOperationHid);
        getMetadata().put("utxoBalance", utxoBalance);
        getMetadata().put("expectedDebtInSat", expectedDebtInSat); // Should be negative
    }
}
