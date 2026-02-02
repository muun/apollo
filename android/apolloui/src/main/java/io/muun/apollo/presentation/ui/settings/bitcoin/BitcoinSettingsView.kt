package io.muun.apollo.presentation.ui.settings.bitcoin

import io.muun.apollo.domain.model.UserActivatedFeatureStatus
import io.muun.apollo.presentation.ui.base.BaseView

interface BitcoinSettingsView : BaseView {

    fun setTaprootByDefault(taprootByDefault: Boolean)

    fun setTaprootStatus(status: UserActivatedFeatureStatus, estimatedHours: Int)

    fun setLoading(loading: Boolean)

}