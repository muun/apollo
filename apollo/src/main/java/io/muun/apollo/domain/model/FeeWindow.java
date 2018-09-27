package io.muun.apollo.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.threeten.bp.ZonedDateTime;

import javax.validation.constraints.NotNull;

public class FeeWindow {

    // time before this fee estimation is considered too old to use
    private static final int EXPIRATION_TIME_MINUTES = 5;

    @NotNull
    public final Long houstonId;

    @NotNull
    public final ZonedDateTime fetchDate;

    @NotNull
    public final Long feeInSatoshisPerByte;

    /**
     * Constructor.
     */
    public FeeWindow(@NotNull Long houstonId,
                     @NotNull ZonedDateTime fetchDate,
                     @NotNull Long feeInSatoshisPerByte) {

        this.houstonId = houstonId;
        this.fetchDate = fetchDate;
        this.feeInSatoshisPerByte = feeInSatoshisPerByte;
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
}
