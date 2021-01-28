package io.muun.apollo.presentation.ui.show_qr

import io.muun.apollo.R
import io.muun.apollo.domain.action.OperationActions
import io.muun.apollo.presentation.analytics.AnalyticsEvent
import io.muun.apollo.presentation.ui.base.BasePresenter
import io.muun.apollo.presentation.ui.base.BaseView
import io.muun.apollo.presentation.ui.base.di.PerActivity
import io.muun.apollo.presentation.ui.show_qr.ShowQrActivity.ORIGIN
import javax.inject.Inject

@PerActivity
class ShowQrPresenter @Inject constructor(
    private val operationActions: OperationActions
): BasePresenter<BaseView>(), QrParentPresenter {

    override fun shareQrContent(content: String) {

        navigator.shareText(context, content, context.getString(R.string.address_share_title))
        analytics.report(AnalyticsEvent.E_ADDRESS_SHARE_TOUCHED())
    }

    override fun copyQrContent(content: String, origin: AnalyticsEvent.ADDRESS_ORIGIN) {
        operationActions.copyQrContentToClipboard(content)
        view.showTextToast(context.getString(R.string.show_qr_copied))
        analytics.report(AnalyticsEvent.E_ADDRESS_COPIED(origin))
    }

    override fun getOrigin(): AnalyticsEvent.RECEIVE_ORIGIN {
        return view.argumentsBundle.getSerializable(ORIGIN) as AnalyticsEvent.RECEIVE_ORIGIN
    }
}
