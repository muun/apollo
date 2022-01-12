package io.muun.apollo.presentation.ui.fragments.new_op_error

import android.os.Bundle
import android.view.View
import android.widget.TextView
import butterknife.BindView
import butterknife.OnClick
import icepick.State
import io.muun.apollo.R
import io.muun.apollo.domain.libwallet.adapt
import io.muun.apollo.domain.model.BitcoinUnit
import io.muun.apollo.domain.utils.locale
import io.muun.apollo.presentation.ui.base.SingleFragment
import io.muun.apollo.presentation.ui.helper.MoneyHelper
import io.muun.apollo.presentation.ui.listener.OnBackPressedListener
import io.muun.apollo.presentation.ui.new_operation.NewOperationErrorType
import io.muun.apollo.presentation.ui.new_operation.NewOperationErrorType.*
import io.muun.apollo.presentation.ui.utils.UiUtils
import io.muun.apollo.presentation.ui.utils.getStyledString
import newop.BalanceErrorState
import newop.ErrorState

class NewOperationErrorFragment : SingleFragment<NewOperationErrorPresenter>(),
    NewOperationErrorView, OnBackPressedListener {

    companion object {

        /**
         * Create a NewOperationErrorFragment with arguments.
         */
        fun create(errorType: NewOperationErrorType): NewOperationErrorFragment {
            val fragment = NewOperationErrorFragment()
            fragment.arguments = createArguments(errorType)
            return fragment
        }

        private fun createArguments(errorType: NewOperationErrorType): Bundle {
            val arguments = Bundle()
            arguments.putString(NewOperationErrorView.ARG_ERROR_TYPE, errorType.name)
            return arguments
        }
    }

    @BindView(R.id.title)
    lateinit var title: TextView

    @BindView(R.id.description)
    lateinit var description: TextView

    @BindView(R.id.insuffient_funds_extras)
    lateinit var insufficientFundsExtras: View

    @BindView(R.id.insuffient_funds_extras_amount)
    lateinit var insufficientFundsAmount: TextView

    @BindView(R.id.insuffient_funds_extras_balance)
    lateinit var insufficientFundsBalance: TextView

    @State
    lateinit var btcUnit: BitcoinUnit

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource(): Int =
        R.layout.new_operation_error_fragment

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        UiUtils.lastResortHideKeyboard(parentActivity)
    }

    override fun setErrorType(errorType: NewOperationErrorType, errorState: ErrorState?) {
        title.setText(getTitleRes(errorType))
        description.text = getDescription(errorType, errorState)
        description.setOnClickListener {

            if (errorType in arrayOf(INVALID_SWAP, INVOICE_NO_ROUTE)) {
                presenter!!.contactSupport()
                finishActivity()

            } else if (errorType == GENERIC) {
                presenter!!.sendErrorReport()
            }
        }
    }

    override fun setBitcoinUnit(bitcoinUnit: BitcoinUnit) {
        btcUnit = bitcoinUnit
    }

    override fun setBalanceErrorState(state: BalanceErrorState) {

        val minBalance = state.totalAmount.adapt()
        val balance = state.balance.adapt()

        insufficientFundsAmount.text = MoneyHelper.formatLongMonetaryAmount(
                minBalance, btcUnit, requireContext().locale()
        )
        insufficientFundsBalance.text = MoneyHelper.formatLongMonetaryAmount(
                balance, btcUnit, requireContext().locale()
        )
        insufficientFundsExtras.visibility = View.VISIBLE
    }

    private fun getTitleRes(errorType: NewOperationErrorType): Int =
        when (errorType) {
            INVALID_ADDRESS -> R.string.error_op_invalid_address_title
            AMOUNT_TOO_SMALL -> R.string.error_op_amount_too_small_title
            INSUFFICIENT_FUNDS -> R.string.error_op_insufficient_funds
            INVOICE_UNREACHABLE_NODE -> R.string.error_op_invoice_unreachable_node_title
            INVOICE_NO_ROUTE -> R.string.error_op_invoice_no_route_title
            INVOICE_WILL_EXPIRE_SOON -> R.string.error_op_invoice_will_expire_soon_title
            INVOICE_EXPIRED -> R.string.error_op_invoice_expired_title
            INVOICE_ALREADY_USED -> R.string.error_op_invoice_used_title
            INVOICE_MISSING_AMOUNT -> R.string.error_op_invoice_invalid_amount_title
            INVALID_INVOICE -> R.string.error_op_invoice_invalid_title
            EXCHANGE_RATE_WINDOW_TOO_OLD -> R.string.error_op_exchange_rate_window_too_old_title
            INVALID_SWAP -> R.string.error_op_generic
            CYCLICAL_SWAP -> R.string.error_op_cyclical_swap_title
            GENERIC -> R.string.error_op_generic
        }

    private fun getDescriptionRes(errorType: NewOperationErrorType): Int =
        when (errorType) {
            INVALID_ADDRESS -> R.string.error_op_invalid_address_desc
            AMOUNT_TOO_SMALL -> R.string.error_op_amount_too_small_desc
            INSUFFICIENT_FUNDS -> R.string.error_op_insufficient_funds_desc
            INVOICE_UNREACHABLE_NODE -> R.string.error_op_invoice_unreachable_node_desc
            INVOICE_NO_ROUTE -> R.string.error_op_invoice_no_route_desc
            INVOICE_WILL_EXPIRE_SOON -> R.string.error_op_invoice_will_expire_soon_desc
            INVOICE_EXPIRED -> R.string.error_op_invoice_expired_desc
            INVOICE_ALREADY_USED -> R.string.error_op_invoice_used_desc
            INVOICE_MISSING_AMOUNT -> R.string.error_op_invoice_invalid_amount_desc
            INVALID_INVOICE -> R.string.error_op_invoice_invalid_desc
            EXCHANGE_RATE_WINDOW_TOO_OLD -> R.string.error_op_exchange_rate_window_too_old_desc
            INVALID_SWAP -> R.string.error_op_generic_desc
            CYCLICAL_SWAP -> R.string.error_op_cyclical_swap_desc
            GENERIC -> R.string.error_op_generic_desc
        }

    private fun getDescription(errorType: NewOperationErrorType, state: ErrorState?): CharSequence {
        val descriptionResId = getDescriptionRes(errorType)

        return when (errorType) {
            INVALID_ADDRESS -> getStyledString(descriptionResId, state!!.paymentIntent.uri.uri)
            else -> getStyledString(descriptionResId)
        }
    }

    @OnClick(R.id.exit)
    fun onExitClick() {
        presenter!!.goHomeInDefeat()
    }

    override fun onBackPressed(): Boolean {
        onExitClick()
        return true
    }
}