package io.muun.apollo.domain.model.base

open class PersistentModel protected constructor(open var id: Long?) {

    override fun equals(other: Any?): Boolean {

        if (this === other) {
            return true
        }

        return if (other !is PersistentModel) {
            false
        } else id != null && id == other.id

    }

    override fun hashCode(): Int {
        return if (id != null) id!!.hashCode() else 0
    }
}
