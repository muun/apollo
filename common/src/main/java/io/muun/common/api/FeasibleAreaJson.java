package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FeasibleAreaJson {

    @NotNull
    public List<List<Integer>> boundary;

    @JsonProperty("total_time_quantiles")
    public Map<String, Double> totalTimeQuantiles;

    @JsonCreator
    public FeasibleAreaJson(
            @JsonProperty("boundary") List<List<Integer>> boundary,
            @JsonProperty("total_time_quantiles") Map<String, Double> totalTimeQuantiles
    ) {
        this.boundary = boundary;
        this.totalTimeQuantiles = totalTimeQuantiles;
    }

}
