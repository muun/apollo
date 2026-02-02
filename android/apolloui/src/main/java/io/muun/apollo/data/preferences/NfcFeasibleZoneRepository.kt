package io.muun.apollo.data.preferences

import android.content.Context
import io.muun.apollo.domain.model.FeasibleZone
import javax.inject.Inject

class NfcFeasibleZoneRepository @Inject constructor(
    context: Context,
    repositoryRegistry: RepositoryRegistry,
) : BaseRepository(context, repositoryRegistry) {

    override val fileName get() = "nfc_feasible_zone"

    private var _feasibleZone: FeasibleZone? = null
    private val lock = Any()

    /**
     * Get the stored FeasibleZone.
     */
    val feasibleZone: FeasibleZone?
        get() = synchronized(lock) {
            _feasibleZone
        }

    /**
     * Store the Feasible Zone.
     */
    fun storeFeasibleZone(feasibleZone: FeasibleZone) {
        synchronized(lock) {
            _feasibleZone = feasibleZone
        }
    }

    fun clearFeasibleZone() {
        synchronized(lock) {
            _feasibleZone = null
        }
    }
}