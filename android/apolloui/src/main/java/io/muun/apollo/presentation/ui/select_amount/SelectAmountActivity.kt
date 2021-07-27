package io.muun.apollo.presentation.ui.select_amount

import android.content.Context
import android.content.Intent
import android.os.Bundle
import butterknife.BindView
import butterknife.OnClick
import icepick.State
import io.muun.apollo.R
import io.muun.apollo.data.serialization.SerializationUtils
import io.muun.apollo.domain.model.BitcoinAmount
import io.muun.apollo.domain.model.CurrencyDisplayMode
import io.muun.apollo.presentation.ui.base.BaseActivity
import io.muun.apollo.presentation.ui.helper.MoneyHelper
import io.muun.apollo.presentation.ui.helper.serialize
import io.muun.apollo.presentation.ui.utils.UiUtils
import io.muun.apollo.presentation.ui.view.MuunAmountInput
import io.muun.apollo.presentation.ui.view.MuunButton
import io.muun.apollo.presentation.ui.view.MuunHeader
import io.muun.common.model.ExchangeRateProvider
import org.javamoney.moneta.Money
import javax.money.CurrencyUnit
import javax.money.MonetaryAmount

class SelectAmountActivity : BaseActivity<SelectAmountPresenter>(), SelectAmountView {

    companion object {
        private const val SELECTED_AMOUNT_RESULT = "selected_amount_result"
        const val IS_BTC_ON_CHAIN = "IS_BTC_ON_CHAIN"
        private const val PRE_SELECTED_AMOUNT = "SELECTED_AMOUNT"

        fun getSelectAddressAmountIntent(context: Context, amount: MonetaryAmount? = null): Intent {
            return Intent(context, SelectAmountActivity::class.java)
                .putExtra(IS_BTC_ON_CHAIN, true)
                .putExtra(PRE_SELECTED_AMOUNT, amount?.serialize())
        }

        fun getSelectInvoiceAmountIntent(context: Context, amount: MonetaryAmount? = null): Intent {
            return Intent(context, SelectAmountActivity::class.java)
                .putExtra(IS_BTC_ON_CHAIN, false)
                .putExtra(PRE_SELECTED_AMOUNT, amount?.serialize())
        }

        fun getPreSelectedAmount(bundle: Bundle): MonetaryAmount? {
            val serialization = bundle.getString(PRE_SELECTED_AMOUNT) ?: return null

            return SerializationUtils.deserializeMonetaryAmount(serialization)
        }

        fun getResult(data: Intent): BitcoinAmount? {
            val result = data.getStringExtra(SELECTED_AMOUNT_RESULT) ?: return null
            return SerializationUtils.deserializeBitcoinAmount(result)
        }
    }

    @BindView(R.id.select_amount_header)
    lateinit var header: MuunHeader

    @BindView(R.id.muun_amount_input)
    lateinit var amountInput: MuunAmountInput

    @BindView(R.id.confirm_amount_button)
    lateinit var confirmButton: MuunButton

    @JvmField
    @State
    var currencyDisplayMode: CurrencyDisplayMode? = null

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource(): Int {
        return R.layout.activity_select_amount
    }

    override fun initializeUi() {
        super.initializeUi()

        header.attachToActivity(this)
        header.showTitle(getString(R.string.select_amount_title))
        header.setNavigation(MuunHeader.Navigation.BACK)

        amountInput.isEnabled = false // Wait for ExchangeRateProvider to be fully init
    }

    override fun onResume() {
        super.onResume()
        amountInput.requestFocusInput()
    }

    override fun setCurrencyDisplayMode(mode: CurrencyDisplayMode) {
        currencyDisplayMode = mode
        amountInput.setCurrencyDisplayMode(mode)
    }

    override fun setExchangeRateProvider(exchangeRateProvider: ExchangeRateProvider) {
        amountInput.setExchangeRateProvider(exchangeRateProvider)
        amountInput.setOnChangeListener { amount: MonetaryAmount -> onAmountChange(amount) }
        amountInput.isEnabled = true
        amountInput.requestFocusInput()
    }

    override fun initializeAmountInput(primaryCurrency: CurrencyUnit) {
        val preSelectedAmount = getPreSelectedAmount(argumentsBundle)
        if (preSelectedAmount != null) {
            setAmount(preSelectedAmount)

        } else {
            setAmount(Money.of(0, primaryCurrency))
        }
    }

    private fun setAmount(preSelectedAmount: MonetaryAmount) {
        amountInput.value = preSelectedAmount
        amountInput.setAmountError(false)
        presenter.updateAmount(preSelectedAmount)
    }

    override fun setSecondaryAmount(amount: MonetaryAmount) {
        amountInput.setSecondaryAmount(MoneyHelper.formatLongMonetaryAmount(
            amount,
            true,
            currencyDisplayMode
        ))
    }

    override fun hideSecondaryAmount() {
        amountInput.hideSecondaryAmount()
    }

    override fun setAmountError(showAmountError: Boolean) {
        amountInput.setAmountError(showAmountError)
        confirmButton.isEnabled = !showAmountError

    }

    override fun finishWithResult(resultCode: Int, amount: BitcoinAmount?) {
        val intent = Intent()
        intent.putExtra(SELECTED_AMOUNT_RESULT, amount?.serialize())
        setResult(resultCode, intent)
        finishActivity()
    }

    override fun isPresenterPersistent(): Boolean {
        return true
    }

    @OnClick(R.id.confirm_amount_button)
    fun onConfirmButtonClick() {
        presenter.confirmSelectedAmount()
    }

    private fun onAmountChange(amount: MonetaryAmount) {
        presenter.updateAmount(amount)
        if (amount.isZero) {
            confirmButton.setText(R.string.confirm_empty_amount)
        } else {
            confirmButton.setText(R.string.confirm_amount)
        }
    }
}