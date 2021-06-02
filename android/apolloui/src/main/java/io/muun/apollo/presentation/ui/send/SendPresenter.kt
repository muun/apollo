package io.muun.apollo.presentation.ui.send

import android.os.Bundle
import io.muun.apollo.domain.model.Contact
import io.muun.apollo.domain.model.OperationUri
import io.muun.apollo.domain.selector.ClipboardUriSelector
import io.muun.apollo.domain.selector.P2PStateSelector
import io.muun.apollo.presentation.analytics.AnalyticsEvent
import io.muun.apollo.presentation.ui.base.BasePresenter
import io.muun.apollo.presentation.ui.base.di.PerActivity
import io.muun.apollo.presentation.ui.new_operation.NewOperationOrigin
import javax.inject.Inject
import javax.validation.constraints.NotNull

@PerActivity
class SendPresenter @Inject constructor(
    private val p2PStateSel: P2PStateSelector,
    private val clipboardUriSel: ClipboardUriSelector
): BasePresenter<SendView>() {

    companion object {
        const val MIN_ADDRESS_LENGTH_FOR_VALIDATION = 23
    }

    override fun setUp(arguments: Bundle) {
        super.setUp(arguments)

        setUpContactList()
        setUpClipboard()
    }

    private fun setUpContactList() {
        val observable = p2PStateSel
            .watch()
            .compose(getAsyncExecutor())
            .doOnNext(view::setP2PState)

        subscribeTo(observable)
    }

    private fun setUpClipboard() {
        val observable = clipboardUriSel
            .watch()
            .compose(getAsyncExecutor())
            .doOnNext(view::setClipboardUri)

        subscribeTo(observable)
    }

    fun isValidPartialUri(maybeUri: String) =
        maybeUri.length < MIN_ADDRESS_LENGTH_FOR_VALIDATION || isValidUri(maybeUri)

    fun isValidUri(maybeUri: String) =
        try {
            OperationUri.fromString(maybeUri)
            true
        } catch (e: Throwable) {
            false
        }

    fun selectUriFromPaster(uri: OperationUri) {

        if (uri.lnUrl.isPresent) {
            navigator.navigateToLnUrlWithdrawConfirm(context, uri.lnUrl.get())

        } else {
            navigator.navigateToNewOperation(context, NewOperationOrigin.SEND_CLIPBOARD_PASTE, uri)
        }
        view.finishActivity()
    }

    fun selectUriFromInput(uri: OperationUri) {
        navigator.navigateToNewOperation(context, NewOperationOrigin.SEND_MANUAL_INPUT, uri)
        view.finishActivity()
    }

    fun selectContact(contact: Contact) {
        navigator.navigateToNewOperation(
            context,
            NewOperationOrigin.SEND_CONTACT,
            OperationUri.fromContactHid(contact.hid)
        )

        view.finishActivity()
    }

    fun goToQrScanner() {
        navigator.navigateToScanQr(context)
        view.finishActivity() // TODO this should only finish activity if new op was actually opened
    }

    fun goToP2PSetup() {
        navigator.navigateToSetupP2P(context)
    }

    fun goToSystemSettings() {
        navigator.navigateToSystemSettings(context)
    }

    override fun getEntryEvent() =
        AnalyticsEvent.S_SEND()
}