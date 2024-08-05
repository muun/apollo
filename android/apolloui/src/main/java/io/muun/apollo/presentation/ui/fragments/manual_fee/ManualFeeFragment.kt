package io.muun.apollo.presentation.ui.fragments.manual_fee

import android.text.TextUtils
import android.view.View
import butterknife.BindString
import butterknife.BindView
import butterknife.OnClick
import icepick.State
import io.muun.apollo.R
import io.muun.apollo.domain.model.BitcoinAmount
import io.muun.apollo.domain.model.BitcoinUnit
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.new_operation.TitleAndDescriptionDrawer
import io.muun.apollo.presentation.ui.new_operation.estimateTimeInMs
import io.muun.apollo.presentation.ui.utils.UiUtils
import io.muun.apollo.presentation.ui.view.*
import io.muun.common.Rules
import newop.EditFeeState
import kotlin.math.max

class ManualFeeFragment : SingleFragment<ManualFeePresenter>(), ManualFeeView {

    companion object {
        private val minProtocolFeeRateInVBytes = Rules.toSatsPerVbyte(Rules.OP_MINIMUM_FEE_RATE)
        private val maxFeeRateInVBytes = Rules.toSatsPerVbyte(Rules.OP_MAXIMUM_FEE_RATE)
    }

    @BindView(R.id.fee_options_message)
    lateinit var message: HtmlTextView

    @BindView(R.id.fee_manual_input)
    lateinit var feeInput: FeeManualInput

    @BindView(R.id.status_message)
    lateinit var statusMessage: StatusMessage

    @BindView(R.id.confirm_fee)
    lateinit var confirmButton: MuunButton

    @BindString(R.string.manual_fee_message)
    lateinit var messageText: String

    @BindString(R.string.manual_fee_how_this_works)
    lateinit var howThisWorksText: String

    @State
    lateinit var mBitcoinUnit: BitcoinUnit

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource(): Int {
        return R.layout.manual_fee_selection_fragment
    }

    override fun initializeUi(view: View) {
        val content = TextUtils.concat(
            messageText,
            ". ",
            RichText(howThisWorksText).setLink { onHowThisWorksClick() }
        )

        message.text = content
        feeInput.requestFocusInput()
        confirmButton.isEnabled = feeInput.feeRateInVBytes != null
    }

    override fun setUpHeader() {
        parentActivity.header.setNavigation(MuunHeader.Navigation.BACK)
        parentActivity.header.showTitle(R.string.edit_fee_title)
    }

    override fun setBitcoinUnit(bitcoinUnit: BitcoinUnit) {
        this.mBitcoinUnit = bitcoinUnit
    }

    override fun setState(state: EditFeeState) {
        feeInput.setOnChangeListener { feeRateInSatsPerVByte: Double? ->

            // 1. "Reset" views to initial state, if later analysis decides it, it will change them
            confirmButton.isEnabled = false
            statusMessage.visibility = View.GONE
            feeInput.resetVisibility()

            // 1.5 If feeRate is null, we're back at initial state (empty input), nothing else to do
            if (feeRateInSatsPerVByte == null) {
                return@setOnChangeListener
            }

            val minMempoolFeeRateInVBytes = state.resolved.paymentContext.minFeeRateInSatsPerVByte
            val minFeeRateInVBytes = max(minMempoolFeeRateInVBytes, minProtocolFeeRateInVBytes)

            // TODO currently forgets input currency, which is important for amount display
            val feeData = state.calculateFee(feeRateInSatsPerVByte)

            // 2. Always show analysis data
            feeInput.setBitcoinUnit(mBitcoinUnit)
            feeInput.setFee(BitcoinAmount.fromLibwallet(feeData.amount))
            feeInput.setMaxTimeMs(estimateTimeInMs(feeData.targetBlocks.toInt()))

            // 3. Warning/Error analysis
            val feeWindow = state.resolved.paymentContext.feeWindow

            when {

                feeRateInSatsPerVByte < minFeeRateInVBytes ->
                    handleFeeRateTooLow(minMempoolFeeRateInVBytes)

                !feeData.isFinal -> // aka can't pay for fee
                    handleFeeRateInsufficientFunds()

                feeRateInSatsPerVByte > maxFeeRateInVBytes ->
                    handleFeeRateTooHigh()

                feeRateInSatsPerVByte < state.minFeeRateForTarget(feeWindow.slowConfTarget) ->
                    handleWarningFeeRateLow()

                else -> {
                    // All good!
                    confirmButton.isEnabled = true
                }
            }
        }
    }

    @OnClick(R.id.confirm_fee)
    fun onConfirmFeeClick() {
        presenter!!.confirmFee(feeInput.feeRateInVBytes)
    }

    private fun handleFeeRateTooLow(minMempoolFeeRateInVBytes: Double) {
        val errorTitle: Int
        val errorDesc: Int

        // This fee rate is too low. Is it because it doesn't match current network
        // requirements, or because it's below the protocol-level minimum?
        if (minMempoolFeeRateInVBytes > minProtocolFeeRateInVBytes) {
            errorTitle = R.string.manual_fee_high_traffic_error_message
            errorDesc = R.string.manual_fee_high_traffic_error_desc
        } else {
            errorTitle = R.string.manual_fee_too_low_error_message
            errorDesc = R.string.manual_fee_too_low_error_desc
        }

        val minFeeRateInVBytes = max(minMempoolFeeRateInVBytes, minProtocolFeeRateInVBytes)
        val errorArg = UiUtils.formatFeeRate(minFeeRateInVBytes)
        statusMessage.setError(getString(errorTitle), getString(errorDesc, errorArg))
    }

    private fun handleFeeRateInsufficientFunds() {
        statusMessage.setError(
            R.string.manual_fee_insufficient_funds_message,
            R.string.manual_fee_insufficient_funds_desc
        )
    }

    private fun handleFeeRateTooHigh() {
        val formattedMaxFeeRate = UiUtils.formatFeeRate(maxFeeRateInVBytes)
        statusMessage.setError(
            getString(R.string.manual_fee_too_high_message),
            getString(R.string.manual_fee_too_high_desc, formattedMaxFeeRate)
        )
    }

    private fun handleWarningFeeRateLow() {
        statusMessage.setWarning(
            R.string.manual_fee_low_warning_message,
            R.string.manual_fee_low_warning_desc
        )
        confirmButton.isEnabled = true // just a warning
    }

    private fun onHowThisWorksClick() {
        val dialog = TitleAndDescriptionDrawer()
        dialog.setTitle(R.string.manual_fee_how_this_works_explanation_title)
        dialog.setDescription(getString(R.string.manual_fee_how_this_works_explanation_desc))
        showDrawerDialog(dialog)
        presenter!!.reportShowManualFeeInfo()
    }
}