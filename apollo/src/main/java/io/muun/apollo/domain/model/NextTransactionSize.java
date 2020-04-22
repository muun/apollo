package io.muun.apollo.domain.model;

import io.muun.apollo.data.serialization.SerializationUtils;
import io.muun.apollo.domain.errors.NullExpectedDebtBugError;
import io.muun.common.Supports;
import io.muun.common.model.SizeForAmount;
import io.muun.common.utils.Preconditions;
import io.muun.common.utils.Since;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import timber.log.Timber;

import java.util.List;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class NextTransactionSize {

    @NotNull
    public List<SizeForAmount> sizeProgression;

    @Nullable
    public Long validAtOperationHid;

    @NotNull
    @Since(apolloVersion = Supports.UserDebt.APOLLO)
    private Long expectedDebtInSat;

    /**
     * Manual constructor.
     */
    public NextTransactionSize(List<SizeForAmount> sizeProgression,
                               @Nullable Long validAtOperationHid,
                               Long expectedDebtInSat) {

        this.sizeProgression = sizeProgression;
        this.validAtOperationHid = validAtOperationHid;
        this.expectedDebtInSat = expectedDebtInSat;
    }

    /**
     * Get the UTXO-only balance (without considering debt).
     */
    public long getUtxoBalance() {
        return sizeProgression.isEmpty()
                ? 0
                : sizeProgression.get(sizeProgression.size() - 1).amountInSatoshis;
    }

    /**
     * Get the spendable balance (considering debt).
     */
    public long getUserBalance() {
        return Preconditions.checkNonNegative(getUtxoBalance() - getExpectedDebtInSat());
    }

    /**
     * Json constructor (for Preferences storage).
     */
    public NextTransactionSize() {
    }

    /**
     * Public for jackson de/serialization to work smoothly with private field.
     * Also, we add a little check here to detect a very weird reported issue impossible to
     * reproduce just to be extra safe and be able to diagnose and fix (while at the same time
     * avoiding damage to the wallet).
     */
    public long getExpectedDebtInSat() {
        if (expectedDebtInSat == null) {
            Timber.e(new NullExpectedDebtBugError());
            expectedDebtInSat = 0L;
        }

        return expectedDebtInSat;
    }

    @Override
    public String toString() {
        return SerializationUtils.serializeJson(NextTransactionSize.class, this);
    }

    /**
     * Migration to init expected debt for pre-existing NTSs.
     */
    public NextTransactionSize initExpectedDebt() {
        if (expectedDebtInSat == null) {
            expectedDebtInSat = 0L;
        }

        return this;
    }
}
