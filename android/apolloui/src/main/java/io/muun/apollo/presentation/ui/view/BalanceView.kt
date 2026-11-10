package io.muun.apollo.presentation.ui.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import icepick.State
import io.muun.apollo.R
import io.muun.apollo.databinding.ViewBalanceBinding
import io.muun.apollo.domain.model.BitcoinUnit
import io.muun.apollo.domain.selector.UtxoSetStateSelector
import io.muun.apollo.presentation.ui.bundler.CurrencyUnitBundler
import io.muun.apollo.presentation.ui.fragments.home.HomeFragmentPresenter
import io.muun.apollo.presentation.ui.helper.BitcoinHelper
import io.muun.apollo.presentation.ui.helper.MoneyHelper
import io.muun.apollo.presentation.ui.helper.isBtc
import io.muun.apollo.presentation.ui.utils.FakeCurrencyUnit
import io.muun.apollo.presentation.ui.utils.UiUtils
import io.muun.apollo.presentation.ui.utils.locale
import io.muun.common.exception.MissingCaseError
import io.muun.common.model.Currency
import io.muun.common.model.ExchangeRateProvider
import io.muun.common.utils.BitcoinUtils
import javax.money.CurrencyUnit
import javax.money.Monetary

class BalanceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    style: Int = 0
) : MuunView<ViewBalanceBinding>(context, attrs, style) {

    private val mainAmount get() = binding.balanceMainCurrencyAmount
    private val mainCurrencyCode get() = binding.balanceMainCurrencyCode
    private val secondaryAmount get() = binding.balanceSecondaryCurrencyAmount
    private val clockIcon get() = binding.balanceClockIcon

    private val clockDrawable: Drawable
        get() = requireNotNull(ContextCompat.getDrawable(context, R.drawable.ic_clock))

    private val clockPendingColor: Int
        get() = ContextCompat.getColor(context, R.color.pending_color)

    private val clockRbfColor: Int
        get() = ContextCompat.getColor(context, R.color.rbf_color)

    // State:
    @State
    @JvmField
    var balanceInSatoshis: Long = 0

    @State
    @JvmField
    var bitcoinUnit: BitcoinUnit? = null

    // Setting a default to avoid race condition, paymentCtx may take too long to load/fetch
    // and if state must be saved, null can't be serialized by CurrencyUnitBundler
    @State(CurrencyUnitBundler::class)
    @JvmField
    var primaryCurrency: CurrencyUnit =
        if (isInEditMode) FakeCurrencyUnit() else Monetary.getCurrency("USD")

    @State
    @JvmField
    var hidden: Boolean = false

    @State
    @JvmField
    var clockState: UtxoSetStateSelector.UtxoSetState = UtxoSetStateSelector.UtxoSetState.CONFIRMED

    private var rateProvider: ExchangeRateProvider? = null

    override fun bindingInflater(): BindingInflater<ViewBalanceBinding> {
        return ViewBalanceBinding::inflate
    }

    fun setBalance(homeState: HomeFragmentPresenter.HomeState) {
        val paymentContext = homeState.paymentContext

        this.rateProvider = ExchangeRateProvider(paymentContext.exchangeRateWindow.toJson())
        this.balanceInSatoshis = paymentContext.userBalance
        this.bitcoinUnit = homeState.bitcoinUnit

        this.primaryCurrency = paymentContext.user
            .getPrimaryCurrency(paymentContext.exchangeRateWindow)

        this.clockState = homeState.utxoSetState

        clockIcon.visibility = if (clockState == UtxoSetStateSelector.UtxoSetState.CONFIRMED) View.GONE else View.VISIBLE

        if (clockState == UtxoSetStateSelector.UtxoSetState.PENDING || clockState == UtxoSetStateSelector.UtxoSetState.RBF) {
            DrawableCompat.setTint(clockDrawable, getColor(clockState))
            clockIcon.setImageDrawable(clockDrawable)
        }

        this.hidden = homeState.balanceHidden
        setHidden(hidden)
    }

    private fun getColor(clockState: UtxoSetStateSelector.UtxoSetState): Int {
        return when (clockState) {
            UtxoSetStateSelector.UtxoSetState.RBF -> clockRbfColor
            UtxoSetStateSelector.UtxoSetState.PENDING -> clockPendingColor
            else -> throw MissingCaseError(clockState)
        }
    }

    private fun drawViewState() {
        mainAmount.text = BitcoinHelper.formatShortBitcoinAmount(
            balanceInSatoshis,
            false,
            bitcoinUnit!!,
            locale()
        )
        mainCurrencyCode.text = MoneyHelper.formatCurrency(Currency.BTC.code, bitcoinUnit!!)

        if (!primaryCurrency.isBtc()) {
            val balanceInSat = BitcoinUtils.satoshisToBitcoins(balanceInSatoshis)
            val balance = rateProvider!!.convert(balanceInSat, primaryCurrency)
            secondaryAmount.text = MoneyHelper.formatLongMonetaryAmount(
                balance,
                bitcoinUnit!!,
                locale()
            )

        } else {
            // Avoid changing this widget's height to avoid a layout readjustment
            secondaryAmount.visibility = INVISIBLE
        }
    }

    fun toggleVisibility(): Boolean {
        hidden = !hidden
        setHidden(hidden)
        return hidden
    }

    private fun setHidden(hidden: Boolean) {
        if (hidden) {
            mainAmount.text = "***"
            mainCurrencyCode.visibility = GONE
            secondaryAmount.text = context.getString(R.string.home_balance_tap_to_reveal)
            secondaryAmount.visibility = VISIBLE
            UiUtils.setMarginBottom(clockIcon, UiUtils.spToPx(context, 8))

        } else {
            drawViewState()
            mainCurrencyCode.visibility = VISIBLE
            UiUtils.setMarginBottom(clockIcon, 0)
        }
    }

    fun isFullyInitialized(): Boolean =
        rateProvider != null
}