package io.muun.apollo.domain.model;

import io.muun.common.Rules;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.threeten.bp.ZonedDateTime;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import javax.validation.constraints.NotNull;

public class FeeWindow {

    // time before this fee estimation is considered too old to use
    private static final int EXPIRATION_TIME_MINUTES = 5;

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
