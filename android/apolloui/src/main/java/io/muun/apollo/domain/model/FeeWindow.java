package io.muun.apollo.domain.model;

import io.muun.common.Rules;
import io.muun.common.utils.Preconditions;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.threeten.bp.ZonedDateTime;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import javax.validation.constraints.NotNull;

public class FeeWindow {

    // time before this fee estimation is considered too old to use
    private static final int EXPIRATION_TIME_MINUTES = 5;

    private static final int SWAP_V2_CONF_TARGET = 250; // Approx 2 days

    @NotNull
    public final Long houstonId;

    @NotNull
    public final ZonedDateTime fetchDate;

    @NotNull
    public final SortedMap<Integer, Double> targetedFees = new TreeMap<>();

    @NotNull
    public final Integer fastConfTarget;

    @NotNull
    public final Integer mediumConfTarget;

    @NotNull
    public final Integer slowConfTarget;

    /**
     * Constructor.
     */
    public FeeWindow(@NotNull Long houstonId,
                     @NotNull ZonedDateTime fetchDate,
                     @NotNull Map<Integer, Double> targetedFees,
                     @NotNull Integer fastConfTarget,
                     @NotNull Integer mediumConfTarget,
                     @NotNull Integer slowConfTarget) {

        this.houstonId = houstonId;
        this.fetchDate = fetchDate;
        this.targetedFees.putAll(targetedFees);
        this.fastConfTarget = fastConfTarget;
        this.mediumConfTarget = mediumConfTarget;
        this.slowConfTarget = slowConfTarget;
    }

    /**
     * Return true if this FeeWindow is recent enough to be used.
     */
    @JsonIgnore
    public boolean isRecent() {
        return ZonedDateTime
                .now(fetchDate.getZone())
                .minusMinutes(EXPIRATION_TIME_MINUTES)
                .isBefore(fetchDate);
    }

    /**
     * Get the appropriate fee rate for a given swap (depends on confirmations needed).
     */
    public double getFeeRate(SubmarineSwap swap) {
        Preconditions.checkNotNull(swap);
        Preconditions.checkNotNull(swap.getFundingOutput().getConfirmationsNeeded());
        return getSwapFeeRate(swap.getFundingOutput().getConfirmationsNeeded());
    }

    /**
     * Get the appropriate fee rate for a given swap (depends on confirmations needed). Useful
     * method for when swap doesn't have a fixed amount (e.g AmountLessInvoices + use all funds).
     */
    public double getSwapFeeRate(int confirmationsNeeded) {
        if (confirmationsNeeded == 0) {
            return getMinimumFeeInSatoshisPerByte(SWAP_V2_CONF_TARGET);
        }

        return getFastestFeeInSatoshisPerByte();
    }

    /**
     * Get the fastest fee rate, in satoshis per byte.
     */
    public double getFastestFeeInSatoshisPerByte() {
        final int shortestTarget = targetedFees.firstKey();
        final double satoshisPerByte = targetedFees.get(shortestTarget);

        return satoshisPerByte;
    }

    /**
     * Get the minimum available fee rate that will hit a given confirmation target. We make no
     * guesses (no averages or interpolations), so we might overshoot the fee if data is too sparse.
     */
    public double getMinimumFeeInSatoshisPerByte(int confirmationTarget) {
        Preconditions.checkPositive(confirmationTarget);

        // Walk the available targets backwards, finding the highest target below the given one:
        for (int closestTarget = confirmationTarget; closestTarget > 0; closestTarget--) {
            if (targetedFees.containsKey(closestTarget)) {
                // Found! This is the lowest fee rate that hits the given target.
                return targetedFees.get(closestTarget);
            }
        }

        // No result? This is odd, but not illogical. It means *all* of our available targets
        // are above the requested one. Let's use the fastest:
        final int lowestTarget = targetedFees.firstKey();
        return targetedFees.get(lowestTarget);
    }

    /**
     * Migrate FeeWindow to start using dynamic fee targets, set by houston. We'll initialize with
     * previous fixed values.
     */
    public FeeWindow initDynamicFeeTargets() {
        return new FeeWindow(
                houstonId,
                fetchDate,
                targetedFees,
                Rules.CONF_TARGET_FAST,
                Rules.CONF_TARGET_MID,
                Rules.CONF_TARGET_SLOW
        );
    }

    /**
     * Adapt apollo's (java) model to libwallet's (go).
     */
    public newop.FeeWindow toLibwallet() {
        final newop.FeeWindow libwalletFeeWindow = new newop.FeeWindow();

        for (Integer confTarget : targetedFees.keySet()) {
            //noinspection ConstantConditions
            libwalletFeeWindow.putTargetedFees(
                    confTarget, Rules.toSatsPerVbyte(targetedFees.get(confTarget))
            );
        }

        libwalletFeeWindow.setFastConfTarget(fastConfTarget);
        libwalletFeeWindow.setMediumConfTarget(mediumConfTarget);
        libwalletFeeWindow.setSlowConfTarget(slowConfTarget);

        return libwalletFeeWindow;
    }
}
