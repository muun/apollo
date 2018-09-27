package io.muun.apollo.domain.model.base;

import android.support.annotation.Nullable;

import javax.validation.constraints.NotNull;

public class HoustonModel extends PersistentModel {

    @NotNull
    public Long hid;

    protected HoustonModel(@Nullable Long id, @NotNull Long hid) {
        super(id);
        this.hid = hid;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other == null || getClass() != other.getClass()) {
            return false;
        }

        return hid.equals(((HoustonModel) other).hid);
    }

    @Override
    public int hashCode() {

        return hid.hashCode();
    }
}
