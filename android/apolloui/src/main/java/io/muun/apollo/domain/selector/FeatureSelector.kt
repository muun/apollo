package io.muun.apollo.domain.selector

import io.muun.apollo.data.preferences.FeaturesRepository
import io.muun.apollo.domain.model.MuunFeature
import rx.Observable
import javax.inject.Inject

class FeatureSelector @Inject constructor(
    private val featuresRepository: FeaturesRepository,
) {

    fun fetch(): Observable<List<MuunFeature>> {
        return featuresRepository.fetch()
    }

    fun fetch(feature: MuunFeature): Observable<Boolean> {
        return featuresRepository.fetch()
            .map { features -> features.contains(feature) }
    }

    fun get(feature: MuunFeature): Boolean =
        fetch().toBlocking().first().contains(feature)
}