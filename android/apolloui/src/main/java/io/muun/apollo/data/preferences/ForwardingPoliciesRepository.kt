package io.muun.apollo.data.preferences

import android.content.Context
import io.muun.apollo.data.preferences.adapter.JsonListPreferenceAdapter
import io.muun.apollo.data.preferences.rx.Preference
import io.muun.apollo.domain.model.ForwardingPolicy
import io.muun.common.utils.Preconditions
import java.util.*
import javax.inject.Inject

// Open for mockito to mock/spy
open class ForwardingPoliciesRepository @Inject constructor(
    context: Context,
    repositoryRegistry: RepositoryRegistry
) : BaseRepository(context, repositoryRegistry) {

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
        @Suppress("unused")
        constructor()

        fun toForwardingPolicy(): ForwardingPolicy {
            return ForwardingPolicy(
                identityKey!!,
                feeBaseMsat,
                feeProportionalMillionths,
                cltvExpiryDelta
            )
        }
    }

    private val preference: Preference<List<StoredForwardingPolicy>>
        get() = rxSharedPreferences.getObject(
            KEY,
            emptyList(),
            JsonListPreferenceAdapter(StoredForwardingPolicy::class.java)
        )

    override val fileName get() = "forwarding_policies"

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