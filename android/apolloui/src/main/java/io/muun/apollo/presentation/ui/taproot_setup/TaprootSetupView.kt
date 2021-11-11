package io.muun.apollo.presentation.ui.taproot_setup

import android.os.Bundle
import io.muun.apollo.presentation.ui.base.BaseView

interface TaprootSetupView: BaseView {

    fun goToStep(step: TaprootSetupStep, args: Bundle)

}