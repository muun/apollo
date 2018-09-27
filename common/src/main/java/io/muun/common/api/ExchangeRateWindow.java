package io.muun.common.api;

import io.muun.common.dates.MuunZonedDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExchangeRateWindow {

    @NotNull
    public Long id;

    @NotNull
    public MuunZonedDateTime fetchDate;

    @NotNull
    public Map<String, Double> rates;

    /**
     * Json constructor.
     */
    public ExchangeRateWindow() {
    }

    /**
     * Houston constructor.
     */
    public ExchangeRateWindow(Long id, MuunZonedDateTime fetchDate, Map<String, Double> rates) {

        this.id = id;
        this.fetchDate = fetchDate;
        this.rates = rates;
    }
}
