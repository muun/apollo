package io.muun.apollo.presentation.ui.settings.flags

import android.os.Bundle
import io.muun.apollo.domain.FeatureOverrideStore
import io.muun.apollo.domain.model.MuunFeature
import io.muun.apollo.presentation.ui.base.ParentPresenter
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter
import javax.inject.Inject

class DisableFeatureFlagsPresenter @Inject constructor(
    private val featureOverrideStore: FeatureOverrideStore,
) : SingleFragmentPresenter<DisableFeatureFlagsView, ParentPresenter>() {

    override fun setUp(arguments: Bundle) {
        super.setUp(arguments)

        val isSecurityCardDisabled = featureOverrideStore.isOverridden(MuunFeature.NFC_CARD_V2)
        view.setSecurityCardFlagEnabled(!isSecurityCardDisabled)
    }

    fun disableSecurityCardFeatureFlag(disabled: Boolean) {
        featureOverrideStore.storeOverride(MuunFeature.NFC_CARD_V2, disabled)
    }
}