package io.muun.apollo.domain.selector

import io.muun.apollo.data.preferences.FeaturesRepository
import io.muun.apollo.domain.FeatureOverrideStore
import io.muun.apollo.domain.model.MuunFeature
import rx.Observable
import javax.inject.Inject

class FeatureSelector @Inject constructor(
    private val featuresRepository: FeaturesRepository,
    private val featureOverrideStore: FeatureOverrideStore,
) {

    fun fetch(): Observable<List<MuunFeature>> {
        return fetchWithoutOverrides()
            .map { list ->
                val newList = list.toMutableList()
                newList.removeAll(featureOverrideStore.getFeatureOverrides())
                newList
            }
    }

    fun fetch(feature: MuunFeature): Observable<Boolean> {
        return fetch()
            .map { features -> features.contains(feature) }
    }

    fun get(feature: MuunFeature): Boolean =
        fetch().toBlocking().first().contains(feature)

    fun hasSecurityCardEnabled(): Boolean {
        return get(MuunFeature.NFC_CARD) || get(MuunFeature.NFC_CARD_V2)
    }

    /**
     * Avoid using unless you REALLY know what you're doing. You probably just want to use the
     * fetch with overrides.
     */
    fun fetchWithoutOverrides(): Observable<List<MuunFeature>> {
        return featuresRepository.fetch()
    }

    /**
     * Avoid using unless you REALLY know what you're doing. You probably just want to use the
     * fetch with overrides.
     */
    fun getWithoutOverrides(feature: MuunFeature): Boolean {
        return fetchWithoutOverrides().toBlocking().first().contains(feature)
    }
}