package io.muun.apollo.presentation.ui.settings.lightning

import io.muun.apollo.presentation.ui.base.BaseView
import io.muun.common.model.ReceiveFormatPreference

interface LightningSettingsView: BaseView {

    fun update(turboChannels: Boolean, receivePreference: ReceiveFormatPreference)

    fun setLoading(loading: Boolean)

}