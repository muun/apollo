package io.muun.apollo.presentation.ui.fragments.recommended_fee

import android.text.TextUtils
import android.view.View
import butterknife.BindString
import butterknife.BindView
import icepick.State
import io.muun.apollo.R
import io.muun.apollo.domain.model.BitcoinAmount
import io.muun.apollo.domain.model.BitcoinUnit
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.new_operation.TitleAndDescriptionDrawer
import io.muun.apollo.presentation.ui.new_operation.estimateTimeInMs
import io.muun.apollo.presentation.ui.view.FeeManualItem
import io.muun.apollo.presentation.ui.view.FeeOptionItem
import io.muun.apollo.presentation.ui.view.HtmlTextView
import io.muun.apollo.presentation.ui.view.MuunButton
import io.muun.apollo.presentation.ui.view.MuunHeader
import io.muun.apollo.presentation.ui.view.RichText
import io.muun.apollo.presentation.ui.view.StatusMessage
import io.muun.common.Rules
import newop.EditFeeState
import newop.FeeState

class RecommendedFeeFragment : SingleFragment<RecommendedFeePresenter>(), RecommendedFeeView {

    @BindView(R.id.fee_options_message)
    lateinit var message: HtmlTextView

    @BindView(R.id.fee_option_fast)
    lateinit var feeOptionItemFast: FeeOptionItem

    @BindView(R.id.fee_option_medium)
    lateinit var feeOptionItemMedium: FeeOptionItem

    @BindView(R.id.fee_option_slow)
    lateinit var feeOptionItemSlow: FeeOptionItem

    @BindView(R.id.enter_fee_manually)
    lateinit var feeManualItem: FeeManualItem

    @BindView(R.id.status_message)
    lateinit var statusMessage: StatusMessage

    @BindView(R.id.confirm_fee)
    lateinit var confirmButton: MuunButton

    @BindString(R.string.fee_options_message)
    lateinit var messageText: String

    @BindString(R.string.fee_options_whats_this)
    lateinit var whatsThisText: String

    private var selectedFeeRateInVBytes: Double? = null // Starts with null if no rate zpre-selected

    @State
    lateinit var mBitcoinUnit: BitcoinUnit

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource(): Int {
        return R.layout.recommended_fee_selection_fragment
    }

    override fun initializeUi(view: View) {
        message.text = TextUtils.concat(
            messageText,
            ". ",
            RichText(whatsThisText).setLink { onWhatsThisClick() }
        )

        if (selectedFeeRateInVBytes == null) {
            confirmButton.isEnabled = false
        }

        feeManualItem.setOnClickListener {
            presenter!!.editFeeManually()
        }

        confirmButton.setOnClickListener {
            presenter!!.confirmFee(selectedFeeRateInVBytes!!) // NonNull (feeRate has been selected)
        }
    }

    override fun setUpHeader() {
        parentActivity.header.setNavigation(MuunHeader.Navigation.EXIT)
        parentActivity.header.showTitle(R.string.edit_fee_title)
    }

    override fun setBitcoinUnit(bitcoinUnit: BitcoinUnit) {
        this.mBitcoinUnit = bitcoinUnit
    }

    override fun setState(state: EditFeeState) {

        val feeWindow = state.resolved.paymentContext.feeWindow
        bindFeeOption(feeOptionItemFast, state, feeWindow.fastConfTarget)
        bindFeeOption(feeOptionItemMedium, state, feeWindow.mediumConfTarget)
        bindFeeOption(feeOptionItemSlow, state, feeWindow.slowConfTarget)

        if (state.amountInfo.takeFeeFromAmount) {
            statusMessage.setWarning(
                R.string.use_all_funds_warning_message,
                R.string.fee_options_use_all_funds_warning_desc,
                false,
                ':'
            )
        }

        val feeRateFastInVBytes = state.minFeeRateForTarget(feeWindow.fastConfTarget)
        val feeRateMidInVBytes = state.minFeeRateForTarget(feeWindow.mediumConfTarget)
        val feeRateSlowInVBytes = state.minFeeRateForTarget(feeWindow.slowConfTarget)

        hideDuplicatedFeeRateOptions(feeRateFastInVBytes, feeRateMidInVBytes, feeRateSlowInVBytes)

        val currentFeeRateInVBytes = state.amountInfo.feeRateInSatsPerVByte
        val feeData = state.calculateFee(currentFeeRateInVBytes)

        if (alreadySomeOptionSelected()) { // then we probably resuming from background, already set
            return
        }

        confirmButton.isEnabled = false // will be enabled later if selected fee is OK

        if (feeData.isFinal) {
            when {
                Rules.feeRateEquals(currentFeeRateInVBytes, feeRateFastInVBytes) ->
                    selectFeeOption(feeOptionItemFast, feeRateFastInVBytes)

                Rules.feeRateEquals(currentFeeRateInVBytes, feeRateMidInVBytes) ->
                    selectFeeOption(feeOptionItemMedium, feeRateMidInVBytes)

                Rules.feeRateEquals(currentFeeRateInVBytes, feeRateSlowInVBytes) ->
                    selectFeeOption(feeOptionItemSlow, feeRateSlowInVBytes)

                else ->
                    showManuallySelectedFee(feeData, currentFeeRateInVBytes)
            }
        }
    }

    private fun alreadySomeOptionSelected(): Boolean =
        feeOptionItemFast.isSelected
            || feeOptionItemMedium.isSelected
            || feeOptionItemSlow.isSelected
            || feeManualItem.isSelected

    private fun showManuallySelectedFee(feeData: FeeState, currentFeeRateInVBytes: Double) {
        if (feeData.isFinal) {
            confirmButton.isEnabled = true
            selectedFeeRateInVBytes = currentFeeRateInVBytes
        }
        feeManualItem.bitcoinUnit = mBitcoinUnit
        feeManualItem.amount = BitcoinAmount.fromLibwallet(feeData.amount).inInputCurrency
        feeManualItem.isSelected = true
    }

    private fun bindFeeOption(feeOptionItem: FeeOptionItem, state: EditFeeState, confTarget: Long) {

        val feeRateInVBytes = state.minFeeRateForTarget(confTarget)

        // TODO currently forgets input currency, which is important for amount display
        val feeData = state.calculateFee(feeRateInVBytes)

        feeOptionItem.setBitcoinUnit(mBitcoinUnit)
        feeOptionItem.setMaxTimeMs(estimateTimeInMs(feeData.targetBlocks.toInt()))
        feeOptionItem.setFeeRate(feeRateInVBytes)
        feeOptionItem.setFee(BitcoinAmount.fromLibwallet(feeData.amount))

        if (feeData.isFinal) {
            feeOptionItem.setOnClickListener {
                selectFeeOption(feeOptionItem, feeRateInVBytes)
            }
        } else {
            feeOptionItem.isEnabled = false
        }
    }

    private fun hideDuplicatedFeeRateOptions(fast: Double, medium: Double, slow: Double) {

        if (Rules.feeRateEquals(medium, slow)) {
            feeOptionItemSlow.visibility = View.GONE
        }

        if (Rules.feeRateEquals(fast, medium)) {
            feeOptionItemMedium.visibility = View.GONE
        }
    }

    private fun selectFeeOption(item: FeeOptionItem, feeRateInVBytes: Double) {
        selectedFeeRateInVBytes = feeRateInVBytes

        feeOptionItemFast.isSelected = false
        feeOptionItemMedium.isSelected = false
        feeOptionItemSlow.isSelected = false
        feeManualItem.isSelected = false

        item.isSelected = true
        confirmButton.isEnabled = true
    }

    private fun onWhatsThisClick() {
        val dialog = TitleAndDescriptionDrawer()
        dialog.setTitle(R.string.fee_options_whats_this_explanation_title)
        dialog.setDescription(getString(R.string.fee_options_whats_this_explanation_desc))
        showDrawerDialog(dialog)
        presenter!!.reportShowSelectFeeInfo()
    }
}