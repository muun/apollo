package io.muun.apollo.presentation.ui.send

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import androidx.viewbinding.ViewBinding
import io.muun.apollo.R
import io.muun.apollo.data.external.Globals
import io.muun.apollo.databinding.ActivitySendBinding
import io.muun.apollo.domain.model.OperationUri
import io.muun.apollo.domain.model.P2PState
import io.muun.apollo.presentation.ui.base.SingleFragmentActivity
import io.muun.apollo.presentation.ui.utils.OS
import io.muun.apollo.presentation.ui.view.MuunButton
import io.muun.apollo.presentation.ui.view.MuunButtonLayout
import io.muun.apollo.presentation.ui.view.MuunContactList
import io.muun.apollo.presentation.ui.view.MuunHeader
import io.muun.apollo.presentation.ui.view.MuunUriInput
import io.muun.apollo.presentation.ui.view.MuunUriPaster
import io.muun.apollo.presentation.ui.view.StatusMessage

class SendActivity : SingleFragmentActivity<SendPresenter>(), SendView {

    companion object {
        fun getStartActivityIntent(context: Context) =
            Intent(context, SendActivity::class.java)
    }

    private val binding: ActivitySendBinding
        get() = getBinding() as ActivitySendBinding
    private val muunHeader: MuunHeader
        get() = binding.header

    private val pasteButton: MuunButton
        get() = binding.pasteButton

    private val uriPaster: MuunUriPaster
        get() = binding.uriPaster

    private val uriInput: MuunUriInput
        get() = binding.uriInput

    private val uriStatusMessage: StatusMessage
        get() = binding.uriStatusMessage

    private val contactList: MuunContactList
        get() = binding.contactList


    private val confirmButton: MuunButton
        get() = binding.confirm

    private val buttonLayout: MuunButtonLayout
        get() = binding.buttonLayout

    override fun inject() =
        component.inject(this)

    override fun getLayoutResource() =
        R.layout.activity_send

    override fun bindingInflater(): (LayoutInflater) -> ViewBinding {
        return ActivitySendBinding::inflate
    }

    override fun getHeader() =
        muunHeader

    override fun initializeUi() {
        super.initializeUi()

        header.attachToActivity(this)
        header.showTitle(R.string.send_header_title)
        header.setNavigation(MuunHeader.Navigation.BACK)
        header.setElevated(true)

        uriPaster.onSelectListener = presenter::selectUriFromPaster

        pasteButton.setOnClickListener {
            presenter.pasteFromClipboard()
        }

        contactList.onSelectListener = presenter::selectContact
        contactList.onGoToP2PSetupListener = presenter::goToP2PSetup
        contactList.onGoToSettingsListener = presenter::goToSystemSettings

        uriInput.onScanQrClickListener = presenter::goToQrScanner
        uriInput.onChangeListener = presenter::onUriInputChange

        confirmButton.setOnClickListener {
            confirmButton.setLoading(true)
            presenter.selectUriFromInput(OperationUri.fromString(uriInput.content))
        }

        buttonLayout.setButtonsVisible(false)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        presenter.onUriInputChange(uriInput.content)
    }

    override fun setP2PState(state: P2PState) {
        contactList.state = state

        // Bye bye p2p! Only users that have it enabled already can see it
        // Note: keeping this feature on for CI P2P payments tests
        val enableP2PSetup = state.user.hasP2PEnabled || Globals.INSTANCE.isCI

        contactList.visibility = if (enableP2PSetup) {
            View.VISIBLE
        } else {
            View.GONE
        }

        // SORRY!
        // I made a mistake when writing MuunContactList: when showing an empty state, the view
        // needs to fill available space; but when showing the actual list, it needs to grow. I
        // don't have time to rewrite it now (and take the empty states into a separate view), so
        // this is happening:
        contactList.layoutParams = if (contactList.isShowingContacts()) {
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        } else {
            LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }
    }

    override fun setClipboardStatus(containsPlainText: Boolean) {
        if (OS.supportsClipboardAccessNotification()) {
            pasteButton.visibility = View.VISIBLE
            pasteButton.isEnabled = containsPlainText
        }
    }

    override fun setClipboardUri(uri: OperationUri?) {
        uriPaster.uri = uri
    }

    override fun pasteFromClipboard(clipboardContent: String) {
        uriInput.content = clipboardContent
    }

    override fun updateUriState(uriState: UriState) {
        uriStatusMessage.visibility = View.GONE

        buttonLayout.setButtonsVisible(!uriState.isBlank)

        if (uriState.isPartiallyValid) {

            confirmButton.isEnabled = uriState.isValid

            if (uriState.isLastCopiedFromReceive) {
                uriStatusMessage.visibility = View.VISIBLE
                uriStatusMessage.setWarning(
                    getString(R.string.send_cyclic_payment_warning),
                    "",
                    true,
                    '.'
                )
            }

        } else {
            confirmButton.isEnabled = false

            uriStatusMessage.visibility = View.VISIBLE
            uriStatusMessage.setError(R.string.send_uri_error_title, R.string.send_uri_error_body)
        }
    }
}