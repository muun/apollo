package io.muun.apollo.presentation.ui.send

import android.os.Bundle
import io.muun.apollo.domain.analytics.AnalyticsEvent
import io.muun.apollo.domain.analytics.NewOperationOrigin
import io.muun.apollo.domain.model.Contact
import io.muun.apollo.domain.model.OperationUri
import io.muun.apollo.domain.selector.ClipboardUriSelector
import io.muun.apollo.domain.selector.P2PStateSelector
import io.muun.apollo.presentation.ui.base.BasePresenter
import io.muun.apollo.presentation.ui.base.di.PerActivity
import io.muun.apollo.presentation.ui.utils.OS
import javax.inject.Inject

@PerActivity
class SendPresenter @Inject constructor(
    private val p2PStateSel: P2PStateSelector,
    private val clipboardUriSel: ClipboardUriSelector,
) : BasePresenter<SendView>() {

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
        if (!OS.supportsClipboardAccessNotification()) {
            clipboardUriSel
                .watch()
                .compose(getAsyncExecutor())
                .doOnNext(view::setClipboardUri)
                .let(this::subscribeTo)

        } else {
            clipboardManager
                .watchForPlainText()
                .compose(getAsyncExecutor())
                .doOnNext(view::setClipboardStatus)
                .let(this::subscribeTo)
        }
    }

    fun pasteFromClipboard() {
        view.pasteFromClipboard(clipboardUriSel.getText())
    }

    /**
     * Select which screen to navigate to, based on the content of an OperationUri.
     */
    fun selectUriFromPaster(uri: OperationUri) {
        confirmOperationUri(uri, NewOperationOrigin.SEND_CLIPBOARD_PASTE)
    }

    fun selectUriFromInput(uri: OperationUri) {
        confirmOperationUri(uri, NewOperationOrigin.SEND_MANUAL_INPUT)
    }

    private fun confirmOperationUri(uri: OperationUri, origin: NewOperationOrigin) {
        if (uri.lnUrl.isPresent) {
            navigator.navigateToLnUrlWithdrawConfirm(context, uri.lnUrl.get())

        } else {
            navigator.navigateToNewOperation(context, origin, uri)
        }
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

    fun onUriInputChange(inputContent: String) {
        view.updateUriState(
            UriState(
                inputContent.isBlank(),
                isValidUri(inputContent),
                isValidPartialUri(inputContent),
                clipboardUriSel.isLastCopiedFromReceive(inputContent)
            )
        )
    }

    private fun isValidPartialUri(maybeUri: String) =
        maybeUri.length < MIN_ADDRESS_LENGTH_FOR_VALIDATION || isValidUri(maybeUri)

    private fun isValidUri(maybeUri: String) =
        try {
            OperationUri.fromString(maybeUri)
            true
        } catch (e: Throwable) {
            false
        }

    override fun getEntryEvent() =
        AnalyticsEvent.S_SEND()
}