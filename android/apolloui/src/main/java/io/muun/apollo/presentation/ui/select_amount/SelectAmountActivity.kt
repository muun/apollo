package io.muun.apollo.presentation.ui.select_amount

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import androidx.viewbinding.ViewBinding
import io.muun.apollo.R
import io.muun.apollo.data.serialization.SerializationUtils
import io.muun.apollo.databinding.ActivitySelectAmountBinding
import io.muun.apollo.domain.model.BitcoinAmount
import io.muun.apollo.domain.model.BitcoinUnit
import io.muun.apollo.presentation.ui.base.BaseActivity
import io.muun.apollo.presentation.ui.helper.isBtc
import io.muun.apollo.presentation.ui.helper.serialize
import io.muun.apollo.presentation.ui.view.MuunAmountInput
import io.muun.apollo.presentation.ui.view.MuunButton
import io.muun.apollo.presentation.ui.view.MuunHeader
import io.muun.common.model.ExchangeRateProvider
import org.javamoney.moneta.Money
import javax.money.CurrencyUnit
import javax.money.MonetaryAmount

class SelectAmountActivity : BaseActivity<SelectAmountPresenter>(), SelectAmountView {

    companion object {
        const val IS_BTC_ON_CHAIN = "IS_BTC_ON_CHAIN"
        private const val SELECTED_AMOUNT_RESULT = "selected_amount_result"
        private const val SAT_SELECTED_AS_CURRENCY_RESULT = "sat_selected_as_currency_result"
        private const val PRE_SELECTED_AMOUNT = "SELECTED_AMOUNT"
        private const val SAT_SELECTED_AS_CURRENCY = "sat_selected_as_currency"

        fun getSelectAddressAmountIntent(context: Context,
                                         amount: MonetaryAmount? = null,
                                         satSelectedAsCurrency: Boolean
        ): Intent {
            return Intent(context, SelectAmountActivity::class.java)
                .putExtra(IS_BTC_ON_CHAIN, true)
                .putExtra(PRE_SELECTED_AMOUNT, amount?.serialize())
                .putExtra(SAT_SELECTED_AS_CURRENCY, satSelectedAsCurrency)
        }

        fun getSelectInvoiceAmountIntent(context: Context,
                                         amount: MonetaryAmount? = null,
                                         satSelectedAsCurrency: Boolean
        ): Intent {
            return Intent(context, SelectAmountActivity::class.java)
                .putExtra(IS_BTC_ON_CHAIN, false)
                .putExtra(PRE_SELECTED_AMOUNT, amount?.serialize())
                .putExtra(SAT_SELECTED_AS_CURRENCY, satSelectedAsCurrency)

        }

        fun getPreSelectedAmount(bundle: Bundle): MonetaryAmount? {
            val serialization = bundle.getString(PRE_SELECTED_AMOUNT) ?: return null
            return SerializationUtils.deserializeMonetaryAmount(serialization)
        }

        fun getPreSelectedBitcoinUnit(bundle: Bundle): BitcoinUnit {
            return if (bundle.getBoolean(SAT_SELECTED_AS_CURRENCY)) {
                BitcoinUnit.SATS
            } else {
                BitcoinUnit.BTC
            }
        }

        fun getResult(data: Intent): BitcoinAmount? {
            val result = data.getStringExtra(SELECTED_AMOUNT_RESULT) ?: return null
            return SerializationUtils.deserializeBitcoinAmount(result)
        }

        fun getSatSelectedAsCurrencyResult(data: Intent): Boolean {
            return data.getBooleanExtra(SAT_SELECTED_AS_CURRENCY_RESULT, false)
        }
    }

    private val binding: ActivitySelectAmountBinding
        get() = getBinding() as ActivitySelectAmountBinding

    private val header: MuunHeader
        get() = binding.selectAmountHeader

    private val amountInput: MuunAmountInput
        get() = binding.muunAmountInput

    private val confirmButton: MuunButton
        get() = binding.confirmAmountButton

    override fun inject() {
        component.inject(this)
    }

    override fun getLayoutResource(): Int {
        return R.layout.activity_select_amount
    }

    override fun bindingInflater(): (LayoutInflater) -> ViewBinding {
        return ActivitySelectAmountBinding::inflate
    }

    override fun initializeUi() {
        super.initializeUi()

        header.attachToActivity(this)
        header.showTitle(getString(R.string.select_amount_title))
        header.setNavigation(MuunHeader.Navigation.BACK)

        confirmButton.setOnClickListener {
            onConfirmButtonClick()
        }

        amountInput.isEnabled = false // Wait for ExchangeRateProvider to be fully init
    }

    override fun onResume() {
        super.onResume()
        amountInput.requestFocusInput()
    }

    override fun setExchangeRateProvider(exchangeRateProvider: ExchangeRateProvider) {
        amountInput.setExchangeRateProvider(exchangeRateProvider)
        amountInput.setOnChangeListener { _, newAmount -> onAmountChange(newAmount) }
        amountInput.isEnabled = true
        amountInput.requestFocusInput()
    }

    override fun initializeAmountInput(primaryCurrency: CurrencyUnit, bitcoinUnit: BitcoinUnit) {
        val preSelectedAmount = getPreSelectedAmount(argumentsBundle)
        if (preSelectedAmount != null) {
            amountInput.setInitialBitcoinUnit(getPreSelectedBitcoinUnit(argumentsBundle))
            setAmount(preSelectedAmount)

        } else {
            setAmount(Money.of(0, primaryCurrency))
            // set initial bitcoin unit to user's pref
            amountInput.setInitialBitcoinUnit(bitcoinUnit)
            if (!primaryCurrency.isBtc()) {
                setSecondaryAmount(Money.of(0, "BTC"))
            }
        }
    }

    private fun setAmount(preSelectedAmount: MonetaryAmount) {
        amountInput.value = preSelectedAmount
        amountInput.setAmountError(false)
        presenter.updateAmount(preSelectedAmount)
    }

    override fun setSecondaryAmount(amount: MonetaryAmount) {
        amountInput.setSecondaryAmount(amount)
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
        intent.putExtra(SAT_SELECTED_AS_CURRENCY_RESULT, amountInput.isSatSelectedAsCurrency)
        setResult(resultCode, intent)
        finishActivity()
    }

    override fun isPresenterPersistent(): Boolean {
        return true
    }

    private fun onConfirmButtonClick() {
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