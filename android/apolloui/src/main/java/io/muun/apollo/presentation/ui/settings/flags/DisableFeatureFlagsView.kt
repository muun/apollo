package io.muun.apollo.presentation.ui.settings.flags

import io.muun.apollo.domain.model.MuunFeature
import io.muun.apollo.presentation.ui.base.BaseView

interface DisableFeatureFlagsView : BaseView {

    fun setState(
        features: List<MuunFeature.OverridableFeature.Overridable>,
        featureOverrides: List<MuunFeature.OverridableFeature.Overridable>,
    )

}