package io.muun.apollo.presentation.ui.fragments.ek_intro

import io.muun.apollo.R
import io.muun.apollo.presentation.ui.fragments.flow_intro.FlowIntroFragment
import io.muun.apollo.presentation.ui.fragments.flow_intro.FlowIntroParentPresenter
import io.muun.apollo.presentation.ui.fragments.flow_intro.FlowIntroView

class EmergencyKitIntroFragment: FlowIntroFragment<
    FlowIntroView,
    EmergencyKitIntroPresenter,
    FlowIntroParentPresenter>() {

    override fun inject() =
        component.inject(this)

    override fun getPager() =
        EmergencyKitIntroPager(childFragmentManager)

    override fun getConfirmLabel() =
        R.string.export_keys_intro_action
}