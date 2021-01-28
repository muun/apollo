package io.muun.apollo.presentation.ui.show_qr

import io.muun.apollo.presentation.analytics.AnalyticsEvent
import io.muun.apollo.presentation.ui.base.BaseView
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter

abstract class QrPresenter<ViewT : BaseView> : SingleFragmentPresenter<ViewT, QrParentPresenter>() {

    abstract fun hasLoadedCorrectly(): Boolean

    protected abstract fun getQrContent(): String

    fun copyQrContent(origin: AnalyticsEvent.ADDRESS_ORIGIN) {
        parentPresenter.copyQrContent(getQrContent(), origin)
    }

    fun shareQrContent() {
        parentPresenter.shareQrContent(getQrContent())
    }

    abstract fun showFullContent()

}
