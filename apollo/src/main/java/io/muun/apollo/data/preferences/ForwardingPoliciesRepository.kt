package io.muun.apollo.data.preferences

import android.content.Context
import io.muun.apollo.data.preferences.adapter.JsonListPreferenceAdapter
import io.muun.apollo.data.preferences.rx.Preference
import io.muun.apollo.domain.model.ForwardingPolicy
import io.muun.common.utils.Preconditions
import java.util.*
import javax.inject.Inject

open class ForwardingPoliciesRepository
@Inject constructor(context: Context?) : BaseRepository(context) {

    companion object {
        private const val KEY = "FORWARDING_POLICIES"
    }

    private class StoredForwardingPolicy {
        var identityKey: ByteArray? = null
        var feeBaseMsat: Long = 0
        var feeProportionalMillionths: Long = 0
        var cltvExpiryDelta: Long = 0

        /**
         * Constructor from the model.
         */
        constructor(policy: ForwardingPolicy) {
            identityKey = policy.identityKey
            feeBaseMsat = policy.feeBaseMsat
            feeProportionalMillionths = policy.feeProportionalMillionths
            cltvExpiryDelta = policy.cltvExpiryDelta
        }

        /**
         * JSON constructor.
         */
        constructor() {}

        fun toForwardingPolicy(): ForwardingPolicy {
            return ForwardingPolicy(
                    identityKey!!,
                    feeBaseMsat,
                    feeProportionalMillionths,
                    cltvExpiryDelta
            )
        }
    }

    private val preference: Preference<List<StoredForwardingPolicy>> =
            rxSharedPreferences.getObject(
                    Companion.KEY,
                    emptyList(),
                    JsonListPreferenceAdapter(StoredForwardingPolicy::class.java)
            );

    override fun getFileName(): String {
        return "forwarding_policies"
    }

    fun fetchOne(): List<ForwardingPolicy> {

        val storedPolicies = preference.get()!!
        // We gave the preference a default value so no null here!
        Preconditions.checkNotNull(storedPolicies)

        val result: MutableList<ForwardingPolicy> = ArrayList()
        for (storedPolicy in storedPolicies) {
            result.add(storedPolicy.toForwardingPolicy())
        }

        return result
    }

    open fun store(policies: List<ForwardingPolicy>) {

        val toStore: MutableList<StoredForwardingPolicy> = ArrayList()
        for (policy in policies) {
            toStore.add(StoredForwardingPolicy(policy))
        }

        preference.set(toStore)
    }
}