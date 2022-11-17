package io.muun.apollo.domain.errors;

public class DebtNegativeError extends MuunError {

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
