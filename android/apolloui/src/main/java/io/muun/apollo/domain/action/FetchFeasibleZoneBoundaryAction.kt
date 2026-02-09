package io.muun.apollo.domain.action

import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.data.preferences.NfcFeasibleZoneRepository
import io.muun.apollo.domain.action.base.BaseAsyncAction1
import io.muun.apollo.domain.model.FeasibleZone
import rx.Observable
import javax.inject.Inject

class FetchFeasibleZoneBoundaryAction @Inject constructor(
    private val houstonClient: HoustonClient,
    private val nfcFeasibleZoneRepository: NfcFeasibleZoneRepository,
) : BaseAsyncAction1<String, FeasibleZone>() {
    override fun action(modelName: String?): Observable<FeasibleZone> {
        return nfcFeasibleZoneRepository.feasibleZone?.let { Observable.just(it) }
            ?: houstonClient.fetchFeasibleArea(modelName)
                .doOnSuccess { feasibleZone ->
                    nfcFeasibleZoneRepository.storeFeasibleZone(feasibleZone)
                }
                .toObservable()
    }
}