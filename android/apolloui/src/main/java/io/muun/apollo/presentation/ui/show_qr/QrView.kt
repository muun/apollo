package io.muun.apollo.presentation.ui.show_qr

import io.muun.apollo.presentation.ui.base.BaseView
import io.muun.apollo.presentation.ui.view.EditAmountItem

interface QrView : BaseView, EditAmountItem.EditAmountHandler {

    fun setQrContent(displayContent: String, qrContent: String)

    fun toggleAdvancedSettings()

}
