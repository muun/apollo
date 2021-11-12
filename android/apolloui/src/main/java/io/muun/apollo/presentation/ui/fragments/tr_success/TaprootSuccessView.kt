package io.muun.apollo.presentation.ui.fragments.tr_success

import io.muun.apollo.domain.model.UserActivatedFeatureStatus
import io.muun.apollo.presentation.ui.base.BaseView

interface TaprootSuccessView: BaseView {

    class State(
        val blocksToTaproot: Int,
        val hoursToTaproot: Int,
        val taprootStatus: UserActivatedFeatureStatus
    )

    fun setState(state: State)

}