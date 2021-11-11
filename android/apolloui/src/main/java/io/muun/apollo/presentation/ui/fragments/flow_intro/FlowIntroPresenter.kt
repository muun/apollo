package io.muun.apollo.presentation.ui.fragments.flow_intro

import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter

abstract class FlowIntroPresenter<V, PP>: SingleFragmentPresenter<V, PP>()
    where V: FlowIntroView,
          PP: FlowIntroParentPresenter {

    fun confirmIntroduction() {
        parentPresenter.confirmIntroduction()
    }

    abstract fun reportIntroductionStep(position: Int)
}