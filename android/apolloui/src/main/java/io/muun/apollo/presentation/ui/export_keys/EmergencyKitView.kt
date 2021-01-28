package io.muun.apollo.presentation.ui.export_keys

import io.muun.apollo.presentation.ui.base.BaseView

interface EmergencyKitView: BaseView {

    fun refreshToolbar()

    fun goToStep(step: EmergencyKeysStep)

}