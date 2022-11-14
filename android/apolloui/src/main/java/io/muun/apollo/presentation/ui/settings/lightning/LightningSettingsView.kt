package io.muun.apollo.presentation.ui.settings.lightning

import io.muun.apollo.presentation.ui.base.BaseView

interface LightningSettingsView: BaseView {

    fun update(turboChannels: Boolean, lightningDefaultForReceiving: Boolean)

    fun setLoading(loading: Boolean)

}