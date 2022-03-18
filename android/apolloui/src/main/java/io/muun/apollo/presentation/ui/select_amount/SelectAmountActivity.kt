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
import io.muun.apollo.domain.model.BitcoinUnit
import io.muun.apollo.domain.utils.locale
import io.muun.apollo.presentation.ui.base.BaseActivity
import io.muun.apollo.presentation.ui.helper.MoneyHelper
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

    @BindView(R.id.select_amount_header)
    lateinit var header: MuunHeader

    @BindView(R.id.muun_amount_input)
    lateinit var amountInput: MuunAmountInput

    @BindView(R.id.confirm_amount_button)
    lateinit var confirmButton: MuunButton

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

    override fun setExchangeRateProvider(exchangeRateProvider: ExchangeRateProvider) {
        amountInput.setExchangeRateProvider(exchangeRateProvider)
        amountInput.setOnChangeListener(this::onAmountChange)
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
        amountInput.setSecondaryAmount(
            MoneyHelper.formatLongMonetaryAmount(
                amount,
                true,
                amountInput.bitcoinUnit,
                locale()
            )
        )
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