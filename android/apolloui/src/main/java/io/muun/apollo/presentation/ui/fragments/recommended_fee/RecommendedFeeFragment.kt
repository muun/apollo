package io.muun.apollo.presentation.ui.fragments.recommended_fee

import android.os.Bundle
import android.text.TextUtils
import android.view.View
import butterknife.BindString
import butterknife.BindView
import butterknife.OnClick
import icepick.State
import io.muun.apollo.R
import io.muun.apollo.domain.model.*
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.new_operation.PaymentRequestCompanion.fromBundle
import io.muun.apollo.presentation.ui.new_operation.TitleAndDescriptionDrawer
import io.muun.apollo.presentation.ui.new_operation.toBundle
import io.muun.apollo.presentation.ui.view.*
import io.muun.common.Rules
import io.muun.common.utils.Preconditions
import kotlin.properties.Delegates

class RecommendedFeeFragment : SingleFragment<RecommendedFeePresenter?>(), RecommendedFeeView {

    companion object {

        @JvmStatic
        fun create(payReq: PaymentRequest): RecommendedFeeFragment =
            RecommendedFeeFragment().apply {
                arguments = payReq.toBundle()
            }

        fun getPaymentRequest(bundle: Bundle): PaymentRequest =
            fromBundle(bundle)
    }

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

    private var selectedFeeRate: Double? = null     // Starts with null if no pre-selected fee rate

    @State
    lateinit var cdm: CurrencyDisplayMode

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource(): Int {
        return R.layout.recommended_fee_selection_fragment
    }

    override fun initializeUi(view: View) {
        super.initializeUi(view)
        parentActivity.header.setNavigation(MuunHeader.Navigation.EXIT)
        parentActivity.header.showTitle(R.string.edit_fee_title)
        val content = TextUtils.concat(
            messageText,
            ". ",
            RichText(whatsThisText).setLink { onWhatsThisClick() }
        )
        message.text = content
        if (selectedFeeRate == null) {
            confirmButton.isEnabled = false
        }
    }

    override fun setCurrencyDisplayMode(currencyDisplayMode: CurrencyDisplayMode) {
        this.cdm = currencyDisplayMode
    }

    override fun setPaymentContext(paymentContext: PaymentContext, paymentRequest: PaymentRequest) {
        val feeOptionFast = paymentContext.fastFeeOption
        val feeOptionMid = paymentContext.mediumFeeOption
        val feeOptionSlow = paymentContext.slowFeeOption

        analyzeAndBindFeeOption(feeOptionItemFast, paymentContext, paymentRequest, feeOptionFast)
        analyzeAndBindFeeOption(feeOptionItemMedium, paymentContext, paymentRequest, feeOptionMid)
        analyzeAndBindFeeOption(feeOptionItemSlow, paymentContext, paymentRequest, feeOptionSlow)

        if (paymentRequest.takeFeeFromAmount) {
            statusMessage.setWarning(
                R.string.use_all_funds_warning_message,
                R.string.fee_options_use_all_funds_warning_desc,
                false,
                ':'
            )
        }

        val feeRateFast = feeOptionFast.satoshisPerByte
        val feeRateMid = feeOptionMid.satoshisPerByte
        val feeRateSlow = feeOptionSlow.satoshisPerByte

        hideDuplicatedFeeRateOptions(feeRateFast, feeRateMid, feeRateSlow)

        val currentFeeRate = Preconditions.checkNotNull(paymentRequest.feeInSatoshisPerByte)
        val analysis: PaymentAnalysis = try {
            paymentContext.analyze(paymentRequest)
        } catch (error: Throwable) {
            presenter!!.handleError(error)
            return
        }

        if (alreadySomeOptionSelected()) { // then we probably resuming from background, already set
            return
        }

        confirmButton.isEnabled = false // will be enabled later if selected fee is OK

        if (analysis.canPayWithSelectedFee) {
            when {
                Rules.feeRateEquals(currentFeeRate, feeRateFast) ->
                    selectFeeOption(feeOptionFast, feeOptionItemFast)

                Rules.feeRateEquals(currentFeeRate, feeRateMid) ->
                    selectFeeOption(feeOptionMid, feeOptionItemMedium)

                Rules.feeRateEquals(currentFeeRate, feeRateSlow) ->
                    selectFeeOption(feeOptionSlow, feeOptionItemSlow)

                else ->
                    showManuallySelectedFee(analysis)
            }
        }
    }

    private fun alreadySomeOptionSelected(): Boolean {
        return (feeOptionItemFast.isSelected
            || feeOptionItemMedium.isSelected
            || feeOptionItemSlow.isSelected
            || feeManualItem.isSelected)
    }

    private fun showManuallySelectedFee(analysis: PaymentAnalysis) {
        Preconditions.checkNotNull(analysis)
        if (analysis.canPayWithSelectedFee) {
            confirmButton.isEnabled = true
            selectedFeeRate = analysis.payReq.feeInSatoshisPerByte!! // Always non null by now
        }
        feeManualItem.currencyDisplayMode = cdm
        feeManualItem.amount = analysis.fee!!.inInputCurrency
        feeManualItem.isSelected = true
    }

    private fun analyzeAndBindFeeOption(
        feeOptionItem: FeeOptionItem,
        paymentContext: PaymentContext,
        payReq: PaymentRequest,
        feeOption: FeeOption
    ) {

        val analysis: PaymentAnalysis = try {
            paymentContext.analyze(payReq.withFeeRate(feeOption.satoshisPerByte))
        } catch (error: Throwable) {
            presenter!!.handleError(error)
            return
        }

        feeOptionItem.setCurrencyDisplayMode(cdm)
        feeOptionItem.setMaxTimeMs(feeOption.maxTimeMs)
        feeOptionItem.setFeeRate(feeOption.satoshisPerByte)
        feeOptionItem.setFee(analysis.fee)
        if (analysis.canPayWithSelectedFee) {
            feeOptionItem.setOnClickListener {
                selectFeeOption(feeOption, feeOptionItem)
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

    private fun selectFeeOption(option: FeeOption, item: FeeOptionItem) {
        selectedFeeRate = option.satoshisPerByte

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

    @OnClick(R.id.enter_fee_manually)
    fun onEditFeeManuallyClick() {
        presenter!!.editFeeManually()
    }

    @OnClick(R.id.confirm_fee)
    fun onConfirmFeeClick() {
        presenter!!.confirmFee(selectedFeeRate!!) // NonNull (one option or manually selected)
    }
}