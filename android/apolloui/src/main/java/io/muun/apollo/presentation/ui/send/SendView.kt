package io.muun.apollo.presentation.ui.send

import io.muun.apollo.domain.model.OperationUri
import io.muun.apollo.domain.model.P2PState
import io.muun.apollo.presentation.ui.base.BaseView

interface SendView: BaseView {

    fun setP2PState(state: P2PState)

    fun setClipboardUri(uri: OperationUri?)
}