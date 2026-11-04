package io.muun.apollo.presentation.ui.fragments.recommended_fee

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import icepick.State
import io.muun.apollo.R
import io.muun.apollo.databinding.RecommendedFeeSelectionFragmentBinding
import io.muun.apollo.domain.model.BitcoinAmount
import io.muun.apollo.domain.model.BitcoinUnit
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.new_operation.TitleAndDescriptionDrawer
import io.muun.apollo.presentation.ui.new_operation.estimateTimeInMs
import io.muun.apollo.presentation.ui.view.FeeOptionItem
import io.muun.apollo.presentation.ui.view.MuunHeader
import io.muun.apollo.presentation.ui.view.RichText
import io.muun.common.Rules
import newop.EditFeeState
import newop.FeeState

class RecommendedFeeFragment : SingleFragment<RecommendedFeePresenter>(), RecommendedFeeView {

    private val binding: RecommendedFeeSelectionFragmentBinding
        get() = getBinding() as RecommendedFeeSelectionFragmentBinding

    override fun bindingInflater(): (LayoutInflater, ViewGroup, Boolean) -> ViewBinding {
        return RecommendedFeeSelectionFragmentBinding::inflate
    }

    private var selectedFeeRateInVBytes: Double? = null // Starts with null if no rate pre-selected

    @State
    lateinit var mBitcoinUnit: BitcoinUnit

    override fun inject() {
        component.inject(this)
    }

    override fun initializeUi(view: View) {
        binding.feeOptionsMessage.text = TextUtils.concat(
            resources.getString(R.string.fee_options_message),
            ". ",
            RichText(
                resources.getString(R.string.fee_options_whats_this)
            ).setLink { onWhatsThisClick() }
        )

        if (selectedFeeRateInVBytes == null) {
            binding.confirmFee.isEnabled = false
        }

        binding.enterFeeManually.setOnClickListener {
            presenter!!.editFeeManually()
        }

        binding.confirmFee.setOnClickListener {
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
        bindFeeOption(binding.feeOptionFast, state, feeWindow.fastConfTarget)
        bindFeeOption(binding.feeOptionMedium, state, feeWindow.mediumConfTarget)
        bindFeeOption(binding.feeOptionSlow, state, feeWindow.slowConfTarget)

        if (state.amountInfo.takeFeeFromAmount) {
            binding.statusMessage.setWarning(
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

        binding.confirmFee.isEnabled = false // will be enabled later if selected fee is OK

        if (feeData.isFinal) {
            when {
                Rules.feeRateEquals(currentFeeRateInVBytes, feeRateFastInVBytes) ->
                    selectFeeOption(binding.feeOptionFast, feeRateFastInVBytes)

                Rules.feeRateEquals(currentFeeRateInVBytes, feeRateMidInVBytes) ->
                    selectFeeOption(binding.feeOptionMedium, feeRateMidInVBytes)

                Rules.feeRateEquals(currentFeeRateInVBytes, feeRateSlowInVBytes) ->
                    selectFeeOption(binding.feeOptionSlow, feeRateSlowInVBytes)

                else ->
                    showManuallySelectedFee(feeData, currentFeeRateInVBytes)
            }
        }
    }

    private fun alreadySomeOptionSelected(): Boolean =
        binding.feeOptionFast.isSelected
            || binding.feeOptionMedium.isSelected
            || binding.feeOptionSlow.isSelected
            || binding.enterFeeManually.isSelected

    private fun showManuallySelectedFee(feeData: FeeState, currentFeeRateInVBytes: Double) {
        if (feeData.isFinal) {
            binding.confirmFee.isEnabled = true
            selectedFeeRateInVBytes = currentFeeRateInVBytes
        }

        binding.enterFeeManually.bitcoinUnit = mBitcoinUnit
        binding.enterFeeManually.amount =
            BitcoinAmount.fromLibwallet(feeData.amount).inInputCurrency
        binding.enterFeeManually.isSelected = true
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
            binding.feeOptionSlow.visibility = View.GONE
        }

        if (Rules.feeRateEquals(fast, medium)) {
            binding.feeOptionMedium.visibility = View.GONE
        }
    }

    private fun selectFeeOption(item: FeeOptionItem, feeRateInVBytes: Double) {
        selectedFeeRateInVBytes = feeRateInVBytes

        binding.feeOptionFast.isSelected = false
        binding.feeOptionMedium.isSelected = false
        binding.feeOptionSlow.isSelected = false
        binding.enterFeeManually.isSelected = false

        item.isSelected = true
        binding.confirmFee.isEnabled = true
    }

    private fun onWhatsThisClick() {
        val dialog = TitleAndDescriptionDrawer()
        dialog.setTitle(R.string.fee_options_whats_this_explanation_title)
        dialog.setDescription(getString(R.string.fee_options_whats_this_explanation_desc))
        showDrawerDialog(dialog)
        presenter!!.reportShowSelectFeeInfo()
    }
}