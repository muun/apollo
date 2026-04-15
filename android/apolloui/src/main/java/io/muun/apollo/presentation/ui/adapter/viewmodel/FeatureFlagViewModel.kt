package io.muun.apollo.presentation.ui.adapter.viewmodel

import io.muun.apollo.domain.model.MuunFeature
import io.muun.apollo.presentation.ui.adapter.holder.ViewHolderFactory

class FeatureFlagViewModel(
    val overridableFeature: MuunFeature.OverridableFeature.Overridable,
    val state: State,
) : ItemViewModel {

    enum class State {
        ENABLED,
        DISABLED
    }

    override fun type(typeFactory: ViewHolderFactory): Int {
        return typeFactory.getLayoutRes(this)
    }
}