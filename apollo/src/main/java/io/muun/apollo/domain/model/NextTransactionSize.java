package io.muun.apollo.domain.model;

import io.muun.apollo.data.serialization.SerializationUtils;
import io.muun.common.model.SizeForAmount;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

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

    /**
     * Manual constructor.
     */
    public NextTransactionSize(List<SizeForAmount> sizeProgression,
                               @Nullable Long validAtOperationHid) {

        this.sizeProgression = sizeProgression;
        this.validAtOperationHid = validAtOperationHid;
    }

    /**
     * Json constructor (for Preferences storage).
     */
    public NextTransactionSize() {
    }

    @Override
    public String toString() {
        return SerializationUtils.serializeList(SizeForAmount.class, sizeProgression);
    }
}
