package io.muun.common.crypto.hd;

import javax.annotation.Nullable;

public class ChildNumber {

    private final int index;

    private final boolean isHardened;

    @Nullable
    private final String comment;

    /**
     * Creates a child number.
     */
    public ChildNumber(int index, boolean isHardened, @Nullable String comment) {
        this.index = index;
        this.isHardened = isHardened;
        this.comment = comment;
    }

    public int getIndex() {
        return index;
    }

    public boolean isHardened() {
        return isHardened;
    }

    @Nullable
    public String getComment() {
        return comment;
    }

    @Override
    public boolean equals(Object other) {

        if (this == other) {
            return true;
        }

        if (!(other instanceof ChildNumber)) {
            return false;
        }

        final ChildNumber otherChildNumber = (ChildNumber) other;

        return index == otherChildNumber.index && isHardened == otherChildNumber.isHardened;
    }

    @Override
    public String toString() {

        String result = String.valueOf(index);

        if (isHardened) {
            result = result + "'";
        }

        if (comment != null) {
            result = comment + ":" + result;
        }

        return result;
    }
}
