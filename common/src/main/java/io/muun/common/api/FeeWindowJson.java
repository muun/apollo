package io.muun.common.api;

import io.muun.common.dates.MuunZonedDateTime;
import io.muun.common.utils.Deprecated;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.SortedMap;
import java.util.TreeMap;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FeeWindowJson {

    @NotNull
    public Long id;

    @NotNull
    public MuunZonedDateTime fetchDate;

    @NotNull
    @Deprecated(atApolloVersion = 40, atFalconVersion = 1)
    public Long feeInSatoshisPerByte;

    @NotNull
    public SortedMap<Integer, Double> targetedFees = new TreeMap<>();

    /**
     * Json constructor.
     */
    public FeeWindowJson() {
    }

    /**
     * Houston constructor.
     */
    public FeeWindowJson(long id,
                         MuunZonedDateTime fetchDate,
                         long feeInSatoshisPerByte,
                         SortedMap<Integer, Double> targetedFees) {

        this.id = id;
        this.fetchDate = fetchDate;
        this.feeInSatoshisPerByte = feeInSatoshisPerByte;
        this.targetedFees = targetedFees;
    }
}
