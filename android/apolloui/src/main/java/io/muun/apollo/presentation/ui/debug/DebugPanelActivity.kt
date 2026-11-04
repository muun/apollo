package io.muun.apollo.presentation.ui.debug

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.text.InputType
import android.view.LayoutInflater
import android.widget.CompoundButton
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.viewbinding.ViewBinding
import io.muun.apollo.data.external.HoustonConfig
import io.muun.apollo.databinding.DebugActivityBinding
import io.muun.apollo.presentation.ui.base.BaseActivity
import io.muun.apollo.presentation.ui.base.BaseView
import rx.functions.Action1
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
    }

    @Inject
    lateinit var houstonConfig: HoustonConfig

    private val binding: DebugActivityBinding
        get() = getBinding() as DebugActivityBinding

    override fun bindingInflater(): (LayoutInflater) -> ViewBinding {
        return DebugActivityBinding::inflate
    }

    override fun inject() {
        component.inject(this)
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

            debugButtonEnterDiagnosticMode.setOnClickListener {
                presenter.enterDiagnosticMode()
            }

            debugButtonForceErrorReport.setOnClickListener {
                presenter.forceErrorReport()
            }

            debugButtonForceErrorReportDialog.setOnClickListener {
                presenter.forceErrorReportDialog()
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
}
