package io.muun.apollo.presentation.ui.debug

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.text.InputType
import android.view.LayoutInflater
import android.widget.CompoundButton
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.viewbinding.ViewBinding
import io.muun.apollo.R
import io.muun.apollo.data.external.HoustonConfig
import io.muun.apollo.data.nfc.api.NfcSession
import io.muun.apollo.databinding.DebugActivityBinding
import io.muun.apollo.presentation.ui.activity.extension.MuunDialog
import io.muun.apollo.presentation.ui.activity.extension.NfcReaderModeExtension
import io.muun.apollo.presentation.ui.base.BaseActivity
import io.muun.apollo.presentation.ui.base.BaseView
import io.muun.apollo.presentation.ui.view.RichText
import rx.functions.Action1
import timber.log.Timber
import javax.inject.Inject

class DebugPanelActivity : BaseActivity<DebugPanelPresenter>(), BaseView {

    companion object {
        /**
         * Creates an intent to launch this activity.
         */
        @JvmStatic
        fun getStartActivityIntent(context: Context): Intent {
            return Intent(context, DebugPanelActivity::class.java)
        }

        private enum class NfcPairingState {
            NONE,
            PAIRING,
            UNPAIRING
        }
    }

    @Inject
    lateinit var nfcReaderModeExtension: NfcReaderModeExtension

    @Inject
    lateinit var houstonConfig: HoustonConfig

    private val binding: DebugActivityBinding
        get() = getBinding() as DebugActivityBinding

    override fun bindingInflater(): (LayoutInflater) -> ViewBinding {
        return DebugActivityBinding::inflate
    }

    private var pairingState: NfcPairingState = NfcPairingState.NONE

    override fun inject() {
        component.inject(this)
    }

    override fun setUpExtensions() {
        super.setUpExtensions()
        addExtension(nfcReaderModeExtension)
    }

    override fun getLayoutResource(): Int {
        return R.layout.debug_activity
    }

    override fun initializeUi() {
        super.initializeUi()

        with(binding) {
            debugButtonFundWalletOnchain.setOnClickListener { presenter.fundThisWalletOnChain() }
            debugButtonFundWalletOffchain.setOnClickListener { presenter.fundThisWalletOffChain() }
            debugButtonGenerateBlocks.setOnClickListener { presenter.generateBlock() }
            debugButtonDropLastTx.setOnClickListener { presenter.dropLastTxFromMempool() }

            debugButtonDropTx.setOnClickListener {
                handleTxIdInput { txId: String ->
                    presenter.dropTx(txId)
                }
            }
            debugButtonUndropTx.setOnClickListener {
                handleTxIdInput { txId: String ->
                    presenter.undropTx(txId)
                }
            }

            debugButtonPairSecurityCard.setOnClickListener {
                pairingState = NfcPairingState.PAIRING
                // Turn on NFC reader Mode
                enableReaderMode()

                // Show Simple Message Dialog - prompt card tap
                MuunDialog.Builder()
                    .title("Pair Security Card")
                    .message("Please tap your device with the security card to confirm Pairing.")
                    .onDismiss {
                        disableReaderMode()
                        pairingState = NfcPairingState.NONE
                    }
                    .build()
                    .let(this@DebugPanelActivity::showDialog)
            }

            debugButtonUnpairSecurityCard.setOnClickListener {
                pairingState = NfcPairingState.UNPAIRING
                // Turn on NFC reader Mode
                enableReaderMode()

                // Show Simple Message Dialog - prompt card tap
                MuunDialog.Builder()
                    .title("Unpair Security Card")
                    .message("Please tap your device with the security card to confirm Unpairing.")
                    .onDismiss {
                        disableReaderMode()
                        pairingState = NfcPairingState.NONE
                    }
                    .build()
                    .let(this@DebugPanelActivity::showDialog)
            }

            debugButtonEnterDiagnosticMode.setOnClickListener {
                presenter.enterDiagnosticMode()
            }

            debugSwitchAllowMultiSession.setOnCheckedChangeListener { _: CompoundButton, _: Boolean ->
                presenter.toggleMultiSessions()
            }
            debugButtonExpireAllOtherSessions.setOnClickListener { presenter.expireAllSessions() }

            debugButtonFetchOperations.setOnClickListener {
                presenter.fetchReplaceOperations()
            }

            debugButtonUploadPhoneContacts.setOnClickListener {
                presenter.scanReplacePhoneContacts()
            }

            debugButtonFetchContacts.setOnClickListener {
                presenter.fetchReplaceContacts()
            }

            debugButtonSyncRealTimeData.setOnClickListener {
                presenter.syncRealTimeData()
            }

            debugButtonSyncExternalAddressesIndexes.setOnClickListener {
                presenter.syncExternalAddressesIndexes()
            }

            debugButtonIntegrityCheck.setOnClickListener {
                presenter.checkIntegrity()
            }

            debugButtonForceFcmTokenUpdate.setOnClickListener {
                presenter.updateFcmToken()
            }
        }
    }

    private fun handleTxIdInput(handler: Action1<String>) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Title")

        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT
        builder.setView(input)

        builder.setPositiveButton("OK") { _: DialogInterface, _: Int ->
            val inputText = input.text.toString()
            handler.call(inputText)
        }
        builder.setNegativeButton("Cancel") { dialog: DialogInterface, _: Int ->
            dialog.cancel()
        }

        builder.show()
    }

    override fun onResume() {
        super.onResume()
        binding.debugTextServerAddress.text = houstonConfig.url
    }

    override fun onNewNfcSession(nfcSession: NfcSession) {
        when (pairingState) {
            NfcPairingState.PAIRING -> {

                // TODO do proper error handling
                val success = try {
                    presenter.setUpSecurityCard(nfcSession)
                    true
                } catch (e: Exception) {
                    Timber.e(e)
                    false
                }

                if (success) {
                    runOnUiThread {
                        binding.nfcCardPaired.text = RichText("TRUE")
                            .setForegroundColor(ContextCompat.getColor(this, R.color.green_dark))
                            .setBold()

                        MuunDialog.Builder()
                            .title("Pair Security Card")
                            .message("Success!")
                            .build()
                            .let(this::showDialog)
                    }

                } else {
                    runOnUiThread {
                        MuunDialog.Builder()
                            .title("Pair Security Card")
                            .message("Error! See Debug logs or dismiss and try again")
                            .build()
                            .let(this::showDialog)
                    }
                }
            }

            NfcPairingState.UNPAIRING -> {

                // TODO do proper error handling
                val success = try {
                    presenter.resetSecuritytCard(nfcSession)
                    true
                } catch (e: Exception) {
                    Timber.e(e)
                    false
                }

                dismissDialog()

                if (success) {
                    runOnUiThread {
                        binding.nfcCardPaired.text = RichText("FALSE")
                            .setForegroundColor(ContextCompat.getColor(this, R.color.red))
                            .setBold()

                        MuunDialog.Builder()
                            .title("Unpair Security Card")
                            .message("Success!")
                            .build()
                            .let(this::showDialog)
                    }
                } else {
                    runOnUiThread {
                        MuunDialog.Builder()
                            .title("Unpair Security Card")
                            .message("Error! See Debug logs or dismiss and try again")
                            .build()
                            .let(this::showDialog)
                    }
                }
            }

            else -> {
                // Ignore
                Timber.d("NFC: onTagDiscovered when neither pairing nor unpairing")
            }
        }
    }

    private fun enableReaderMode() {
        nfcReaderModeExtension.enableReaderMode()
    }

    private fun disableReaderMode() {
        nfcReaderModeExtension.disableReaderMode()
    }
}
