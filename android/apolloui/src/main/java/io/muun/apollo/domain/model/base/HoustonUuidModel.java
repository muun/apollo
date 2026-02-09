package io.muun.apollo.domain.model.base;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

public class HoustonUuidModel extends PersistentModel {

    @NotNull
    public final String houstonUuid;

    protected HoustonUuidModel(@Nullable Long id, @NotNull String houstonUuid) {
        super(id);
        this.houstonUuid = houstonUuid;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other == null || getClass() != other.getClass()) {
            return false;
        }

        return houstonUuid.equals(((HoustonUuidModel) other).houstonUuid);
    }

    @Override
    public int hashCode() {

        return houstonUuid.hashCode();
    }

    @Override
    public String toString() {
        return String.format("<%s houstonUuid=%s>", getClass().getSimpleName(), houstonUuid);
    }
}
