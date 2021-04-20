package io.muun.apollo.presentation.ui.show_qr

import io.muun.apollo.presentation.ui.base.BaseView

interface QrView : BaseView {

    fun setQrContent(displayContent: String, qrContent: String)

}
