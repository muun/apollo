package io.muun.apollo.presentation.ui.show_qr

import io.muun.apollo.domain.analytics.AnalyticsEvent
import io.muun.apollo.presentation.ui.base.ParentPresenter

interface QrParentPresenter : ParentPresenter {

    fun handleNotificationPermissionPrompt()

    fun reportNotificationPermissionAsked()

    fun shareQrContent(content: String)

    fun copyQrContent(content: String, origin: AnalyticsEvent.ADDRESS_ORIGIN)

    fun getOrigin(): AnalyticsEvent.RECEIVE_ORIGIN

}
