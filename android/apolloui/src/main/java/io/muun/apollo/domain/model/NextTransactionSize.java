package io.muun.apollo.domain.model;

import io.muun.apollo.data.serialization.SerializationUtils;
import io.muun.apollo.domain.errors.DebtNegativeError;
import io.muun.apollo.domain.errors.NullExpectedDebtBugError;
import io.muun.common.Rules;
import io.muun.common.Supports;
import io.muun.common.model.SizeForAmount;
import io.muun.common.model.UtxoStatus;
import io.muun.common.utils.Preconditions;
import io.muun.common.utils.Since;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import timber.log.Timber;

import java.util.ArrayList;
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
        return Preconditions.checkNotNegative(getUtxoBalance() - getExpectedDebtInSat());
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

        if (expectedDebtInSat < 0) {
            // We can't allow negative debt
            Timber.e(
                    new DebtNegativeError(validAtOperationHid, getUtxoBalance(), expectedDebtInSat)
            );
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

    /**
     * Migration to init outpoints for pre-existing NTSs.
     */
    public NextTransactionSize initOutpoints() {

        final List<SizeForAmount> newSizeProgression = new ArrayList<>();
        for (SizeForAmount sizeForAmount : sizeProgression) {
            newSizeProgression.add(sizeForAmount.initOutpoint());
        }

        this.sizeProgression = newSizeProgression;

        return this;
    }

    /**
     * Migration to init utxo status for pre-existing NTSs.
     */
    public NextTransactionSize initUtxoStatus() {

        final List<SizeForAmount> newSizeProgression = new ArrayList<>();
        for (SizeForAmount sizeForAmount : sizeProgression) {
            newSizeProgression.add(sizeForAmount.initUtxoStatusForApollo());
        }

        this.sizeProgression = newSizeProgression;

        return this;
    }

    /**
     * Extract complete list of outpoints, sorted as used in sizeProgression (aka as we use it for
     * our fee computations).
     */
    public List<String> extractOutpoints() {
        final ArrayList<String> outpoints = new ArrayList<>();

        for (SizeForAmount sizeForAmount : sizeProgression) {

            if ("uninitialized".equals(sizeForAmount.outpoint) || sizeForAmount.outpoint == null) {
                continue;
            }

            outpoints.add(sizeForAmount.outpoint);
        }

        // outpoints will be empty for "uninitialized" nts
        Preconditions.checkArgument(
                outpoints.size() == sizeProgression.size() || outpoints.isEmpty()
        );

        // Houston expects the outpoint list (new clients) or null (old clients or "uninitialized"
        // clients, aka NTS not yet re-fetched)
        return outpoints.isEmpty() ? null : outpoints;
    }


    /**
     * Filter and returns a complete list of unconfirmed utxos in current size progression.
     */
    public List<SizeForAmount> getUnconfirmedUtxos() {
        final List<SizeForAmount> unconfirmedUtxos = new ArrayList<>();
        for (SizeForAmount sizeForAmount : sizeProgression) {
            if (sizeForAmount.status == UtxoStatus.UNCONFIRMED) {
                unconfirmedUtxos.add(sizeForAmount);
            }
        }

        return unconfirmedUtxos;
    }

    /**
     * Adapt apollo's (java) model to libwallet's (go).
     */
    public newop.NextTransactionSize toLibwallet() {
        final newop.NextTransactionSize libwalletNts = new newop.NextTransactionSize();

        libwalletNts.setValidAtOperationHid(validAtOperationHid != null ? validAtOperationHid : -1);
        libwalletNts.setExpectedDebtInSat(expectedDebtInSat);

        for (SizeForAmount sizeForAmount : sizeProgression) {
            libwalletNts.addSizeForAmount(toLibwallet(sizeForAmount));
        }

        return libwalletNts;
    }

    /**
     * Placing this method here since SizeForAmount lives in common module (pure java) and
     * can't interact with go code/classes.
     */
    private newop.SizeForAmount toLibwallet(SizeForAmount sizeForAmount) {
        final newop.SizeForAmount libwalletSizeForAmount = new newop.SizeForAmount();

        libwalletSizeForAmount.setAmountInSat(sizeForAmount.amountInSatoshis);
        libwalletSizeForAmount.setSizeInVByte(getSizeInVirtualBytes(sizeForAmount));
        libwalletSizeForAmount.setOutpoint(sizeForAmount.outpoint);
        libwalletSizeForAmount.setUtxoStatus(sizeForAmount.status.toString());

        return libwalletSizeForAmount;
    }

    /**
     * Despite what it says in its name @code{ sizeForAmount.sizeInBytes } holds the value of a
     * tx size in weight units. Yes, it's indescribably confusing. This is due to legacy reasons.
     * At some very old point in time (pre-segwit) these sizes were accounted in what was considered
     * bytes at the time.
     */
    private int getSizeInVirtualBytes(SizeForAmount sizeForAmount) {
        return sizeForAmount.sizeInBytes / Rules.VBYTE_TO_WEIGHT_UNIT_RATIO;
    }
}
