package io.muun.apollo.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.threeten.bp.ZonedDateTime;

import java.util.Map;
import javax.validation.constraints.NotNull;

public class ExchangeRateWindow {

    // time before these rates are considered too old to use
    private static final int EXPIRATION_TIME_MINUTES = 1;

    @NotNull
    public final Long windowHid;

    @NotNull
    public final ZonedDateTime fetchDate;

    @NotNull
    public final Map<String, Double> rates;

    /**
     * Constructor.
     */
    @JsonCreator
    public ExchangeRateWindow(
            @JsonProperty("windowHid") @NotNull Long windowHid,
            @JsonProperty("fetchDate") @NotNull ZonedDateTime fetchDate,
            @JsonProperty("rates") @NotNull Map<String, Double> rates) {

        this.windowHid = windowHid;
        this.fetchDate = fetchDate;
        this.rates = rates;
    }

    /**
     * Return true if this ExchangeRateWindow is recent enough to be used.
     */
    @JsonIgnore
    public boolean isRecent() {
        return ZonedDateTime
                .now(fetchDate.getZone())
                .minusMinutes(EXPIRATION_TIME_MINUTES)
                .isBefore(fetchDate);
    }
}
