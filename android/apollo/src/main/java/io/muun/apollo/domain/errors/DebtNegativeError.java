package io.muun.apollo.domain.errors;

import io.muun.apollo.domain.model.NextTransactionSize;

public class DebtNegativeError extends MuunError {

    public DebtNegativeError(NextTransactionSize nts) {
        getMetadata().put("validAtOperationHid", nts.validAtOperationHid);
        getMetadata().put("utxoBalance", nts.getUtxoBalance());
        getMetadata().put("expectedDebtInSat", nts.getExpectedDebtInSat()); // Should be negative
    }
}
