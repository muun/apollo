package io.muun.apollo.presentation.ui.fragments.ek_intro

import io.muun.apollo.presentation.ui.base.BaseView

interface EmergencyKitIntroView: BaseView {

    companion object {
        const val ARG_STEP = "step"
    }

    fun setLoading(isLoading: Boolean)
}