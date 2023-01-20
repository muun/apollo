package io.muun.apollo.data.preferences

import android.content.Context
import io.muun.apollo.data.preferences.adapter.JsonPreferenceAdapter
import io.muun.apollo.data.preferences.rx.Preference
import io.muun.apollo.data.preferences.stored.StoredBackendFeatures
import io.muun.apollo.domain.model.MuunFeature
import rx.Observable
import javax.inject.Inject

// Open for mockito to mock/spy
open class FeaturesRepository @Inject constructor(
    context: Context,
    repositoryRegistry: RepositoryRegistry,
) : BaseRepository(context, repositoryRegistry) {

    companion object {
        private const val BACKEND_FEATURES = "backend_features"
    }

    private val backendFeaturesPref: Preference<StoredBackendFeatures> = rxSharedPreferences
        .getObject(
            BACKEND_FEATURES,
            StoredBackendFeatures(),
            JsonPreferenceAdapter(StoredBackendFeatures::class.java)
        )

    override fun getFileName() =
        "features"

    /**
     * Fetch an observable instance of the currently supported backend features.
     */
    fun fetch(): Observable<List<MuunFeature>> {
        return backendFeaturesPref.asObservable()
            .map { it.features.map(MuunFeature.Companion::fromLibwalletModel) }
    }

    /**
     * Store a new set of backend features.
     */
    open fun store(newFeatures: List<MuunFeature>) {
        val storedBackendFeatures = backendFeaturesPref.get()!! // Has default value
        storedBackendFeatures.features = newFeatures.map { it.toLibwalletModel() }
        backendFeaturesPref.set(storedBackendFeatures)
    }

}