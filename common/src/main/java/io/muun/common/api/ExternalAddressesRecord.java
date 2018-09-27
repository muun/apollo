package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Null;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExternalAddressesRecord {

    @NotNull
    @Min(-1)
    public Integer maxUsedIndex;

    @Null
    public Integer maxWatchingIndex;

    /**
     * Json constructor.
     */
    public ExternalAddressesRecord() {
    }

    /**
     * Apollo constructor.
     */
    public ExternalAddressesRecord(Integer maxUsedIndex) {
        this.maxUsedIndex = maxUsedIndex;
    }

    /**
     * Houston constructor.
     */
    public ExternalAddressesRecord(Integer maxUsedIndex, Integer maxWatchingIndex) {
        this.maxUsedIndex = maxUsedIndex;
        this.maxWatchingIndex = maxWatchingIndex;
    }
}
