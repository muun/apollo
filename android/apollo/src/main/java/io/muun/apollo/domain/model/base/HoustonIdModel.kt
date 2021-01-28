package io.muun.apollo.domain.model.base

import android.annotation.SuppressLint

open class HoustonIdModel protected constructor(id: Long?, open val hid: Long) : PersistentModel(id) {

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        return if (other == null || javaClass != other.javaClass) {
            false
        } else hid == (other as HoustonIdModel).hid

    }

    override fun hashCode(): Int {

        return hid.hashCode()
    }

    @SuppressLint("DefaultLocale")
    override fun toString(): String {
        return String.format("<%s hid=%d>", javaClass.simpleName, hid)
    }
}
