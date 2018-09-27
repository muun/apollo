package io.muun.common.api;

import io.muun.common.dates.MuunZonedDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FeeWindow {

    @NotNull
    public Long id;

    @NotNull
    public MuunZonedDateTime fetchDate;

    @NotNull
    public Long feeInSatoshisPerByte;

    /**
     * Json constructor.
     */
    public FeeWindow() {
    }

    /**
     * Houston constructor.
     */
    public FeeWindow(Long id, MuunZonedDateTime fetchDate, Long feeInSatoshisPerByte) {

        this.id = id;
        this.fetchDate = fetchDate;
        this.feeInSatoshisPerByte = feeInSatoshisPerByte;
    }
}
