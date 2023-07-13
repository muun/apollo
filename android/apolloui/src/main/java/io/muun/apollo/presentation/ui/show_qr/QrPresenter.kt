package io.muun.apollo.presentation.ui.show_qr

import icepick.State
import io.muun.apollo.domain.analytics.AnalyticsEvent
import io.muun.apollo.presentation.ui.base.SingleFragmentPresenter
import io.muun.apollo.presentation.ui.view.EditAmountItem
import javax.money.MonetaryAmount

abstract class QrPresenter<ViewT : QrView> : SingleFragmentPresenter<ViewT, QrParentPresenter>(),
    EditAmountItem.EditAmountHandler {

    // We need to state-save in presenter 'cause apparently this fragment being inside ViewPager
    // messes up our state saving/restoring for our custom views :'(
    @State
    @JvmField
    var showingAdvancedSettings = false

    protected abstract fun hasLoadedCorrectly(): Boolean

    protected abstract fun getQrContent(): String

    protected abstract fun showFullContentInternal()

    fun showFullContent() {
        if (hasLoadedCorrectly()) {
            showFullContentInternal()
        }
    }

    fun copyQrContent(origin: AnalyticsEvent.ADDRESS_ORIGIN) {
        if (hasLoadedCorrectly()) {
            parentPresenter.copyQrContent(getQrContent(), origin)
        }
    }

    fun shareQrContent() {
        if (hasLoadedCorrectly()) {
            parentPresenter.shareQrContent(getQrContent())
        }
    }

    override fun onEditAmount(amount: MonetaryAmount?) {
        if (hasLoadedCorrectly()) {
            view.onEditAmount(amount)
        }
    }

    fun toggleAdvancedSettings() {
        if (hasLoadedCorrectly()) {
            showingAdvancedSettings = !showingAdvancedSettings
            view.toggleAdvancedSettings()
        }
    }
}
