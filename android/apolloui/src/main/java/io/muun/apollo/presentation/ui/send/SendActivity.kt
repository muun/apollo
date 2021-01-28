package io.muun.apollo.presentation.ui.send

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import butterknife.BindView
import io.muun.apollo.R
import io.muun.apollo.domain.model.OperationUri
import io.muun.apollo.domain.model.P2PState
import io.muun.apollo.presentation.ui.base.SingleFragmentActivity
import io.muun.apollo.presentation.ui.view.MuunButton
import io.muun.apollo.presentation.ui.view.MuunButtonLayout
import io.muun.apollo.presentation.ui.view.MuunContactList
import io.muun.apollo.presentation.ui.view.MuunHeader
import io.muun.apollo.presentation.ui.view.MuunUriInput
import io.muun.apollo.presentation.ui.view.MuunUriPaster
import io.muun.apollo.presentation.ui.view.StatusMessage

class SendActivity: SingleFragmentActivity<SendPresenter>(), SendView {

    companion object {
        fun getStartActivityIntent(context: Context) =
            Intent(context, SendActivity::class.java)
    }

    @BindView(R.id.header)
    lateinit var muunHeader: MuunHeader

    @BindView(R.id.uri_paster)
    lateinit var uriPaster: MuunUriPaster

    @BindView(R.id.uri_input)
    lateinit var uriInput: MuunUriInput

    @BindView(R.id.uri_error_message)
    lateinit var uriError: StatusMessage

    @BindView(R.id.contact_list)
    lateinit var contactList: MuunContactList

    @BindView(R.id.confirm)
    lateinit var confirmButton: MuunButton

    @BindView(R.id.button_layout)
    lateinit var buttonLayout: MuunButtonLayout


    override fun inject() =
        component.inject(this)

    override fun getLayoutResource() =
        R.layout.activity_send

    override fun getHeader() =
        muunHeader

    override fun initializeUi() {
        super.initializeUi()

        header.attachToActivity(this)
        header.showTitle(R.string.send_header_title)
        header.setNavigation(MuunHeader.Navigation.BACK)
        header.setElevated(true)

        uriPaster.onSelectListener = presenter::selectUriFromPaster

        contactList.onSelectListener = presenter::selectContact
        contactList.onGoToP2PSetupListener = presenter::goToP2PSetup
        contactList.onGoToSettingsListener = presenter::goToSystemSettings

        uriInput.onScanQrClickListener = presenter::goToQrScanner
        uriInput.onChangeListener = this::onUriInputChange

        confirmButton.setOnClickListener {
            confirmButton.isLoading = true
            presenter.selectUriFromInput(OperationUri.fromString(uriInput.content))
        }

        buttonLayout.setButtonsVisible(false)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        super.onRestoreInstanceState(savedInstanceState)
        onUriInputChange(uriInput.content)
    }

    override fun setP2PState(state: P2PState) {
        contactList.state = state

        // Bye bye p2p! Only users that have it enabled already can see it
        contactList.visibility = if (state.user.hasP2PEnabled) {
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

    override fun setClipboardUri(uri: OperationUri?) {
        uriPaster.uri = uri
    }

    private fun onUriInputChange(content: String) {
        buttonLayout.setButtonsVisible(content.isNotBlank())

        confirmButton.isEnabled = presenter.isValidUri(content)

        if (presenter.isValidPartialUri(content)) {
            uriError.visibility = View.GONE
        } else {
            uriError.setError(R.string.send_uri_error_title, R.string.send_uri_error_body)
        }
    }
}