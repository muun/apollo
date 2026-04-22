package io.muun.apollo.presentation.ui.settings.flags

import android.os.Bundle
import io.muun.apollo.domain.FeatureOverrideStore
import io.muun.apollo.domain.model.MuunFeature
import io.muun.apollo.domain.selector.FeatureSelector
import io.muun.apollo.presentation.ui.adapter.viewmodel.FeatureFlagViewModel
import io.muun.apollo.presentation.ui.base.ParentPresenter
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter
import javax.inject.Inject

class DisableFeatureFlagsPresenter @Inject constructor(
    private val featureSelector: FeatureSelector,
    private val featureOverrideStore: FeatureOverrideStore,
) : SingleFragmentPresenter<DisableFeatureFlagsView, ParentPresenter>() {

    override fun setUp(arguments: Bundle) {
        super.setUp(arguments)

        view.setState(
            featureSelector.fetchOverridableFlags().toBlocking().first(),
            featureOverrideStore.getFeatureOverrides()
        )
    }

    fun toggleFeatureFlag(
        overridableFeature: MuunFeature.OverridableFeature.Overridable,
        state: FeatureFlagViewModel.State,
    ) {
        // If state was ENABLED -> disable, if state was DISABLED -> enable
        if (state == FeatureFlagViewModel.State.ENABLED) {
            featureOverrideStore.disableFeatureFlag(overridableFeature)
        } else {
            featureOverrideStore.enableFeatureFlag(overridableFeature)
        }
    }
}