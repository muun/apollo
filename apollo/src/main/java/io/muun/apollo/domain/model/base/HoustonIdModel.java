package io.muun.apollo.domain.model.base;

import android.annotation.SuppressLint;
import android.support.annotation.Nullable;

import javax.validation.constraints.NotNull;

public class HoustonIdModel extends PersistentModel {

    @NotNull
    public Long hid;

    protected HoustonIdModel(@Nullable Long id, @NotNull Long hid) {
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

        return hid.equals(((HoustonIdModel) other).hid);
    }

    @Override
    public int hashCode() {

        return hid.hashCode();
    }

    @SuppressLint("DefaultLocale")
    @Override
    public String toString() {
        return String.format("<%s hid=%d>", getClass().getSimpleName(), hid != null ? hid : -1);
    }
}
