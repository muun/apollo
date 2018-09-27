package io.muun.apollo.domain.model.base;

import android.support.annotation.Nullable;

public class PersistentModel {

    @Nullable
    public Long id;

    protected PersistentModel(@Nullable Long id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object other) {

        if (this == other) {
            return true;
        }

        if (!(other instanceof PersistentModel)) {
            return false;
        }

        return id != null && id.equals(((PersistentModel) other).id);
    }

    @Override
    public int hashCode() {

        return id != null ? id.hashCode() : 0;
    }
}
