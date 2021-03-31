package io.muun.apollo.presentation.ui.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.drawable.DrawableCompat
import butterknife.BindColor
import butterknife.BindDrawable
import butterknife.BindView
import icepick.State
import io.muun.apollo.R
import io.muun.apollo.domain.model.CurrencyDisplayMode
import io.muun.apollo.domain.selector.UtxoSetStateSelector
import io.muun.apollo.presentation.ui.bundler.CurrencyUnitBundler
import io.muun.apollo.presentation.ui.fragments.home.HomePresenter
import io.muun.apollo.presentation.ui.helper.BitcoinHelper
import io.muun.apollo.presentation.ui.helper.MoneyHelper
import io.muun.apollo.presentation.ui.utils.UiUtils
import io.muun.common.exception.MissingCaseError
import io.muun.common.model.Currency
import io.muun.common.model.ExchangeRateProvider
import io.muun.common.utils.BitcoinUtils
import javax.money.CurrencyUnit
import javax.money.Monetary

class BalanceView : MuunView {

    // Components:
    @BindView(R.id.balance_main_currency_amount)
    lateinit var mainAmount: TextView

    @BindView(R.id.balance_main_currency_code)
    lateinit var mainCurrencyCode: TextView

    @BindView(R.id.balance_secondary_currency_amount)
    lateinit var secondaryAmount: TextView // Contains Currency Code

    @BindView(R.id.balance_clock_icon)
    lateinit var clockIcon: ImageView

    // Resources:

    @BindDrawable(R.drawable.ic_clock)
    lateinit var clockDrawable: Drawable

    @BindColor(R.color.pending_color)
    @JvmField
    internal var clockPendingColor: Int = 0

    @BindColor(R.color.rbf_color)
    @JvmField
    internal var clockRbfColor: Int = 0

    // State:
    @State
    @JvmField
    var balanceInSatoshis: Long = 0

    @State
    @JvmField
    var mode: CurrencyDisplayMode? = null

    // Setting a default to avoid race condition, paymentCtx may take too long to load/fetch
    // and if state must be saved, null can't be serialized by CurrencyUnitBundler
    @State(CurrencyUnitBundler::class)
    @JvmField
    var primaryCurrency: CurrencyUnit = Monetary.getCurrency("USD")

    @State
    @JvmField
    var hidden: Boolean = false

    @State
    @JvmField
    var clockState: UtxoSetStateSelector.UtxoSetState = UtxoSetStateSelector.UtxoSetState.CONFIRMED

    private var rateProvider: ExchangeRateProvider? = null

    @JvmOverloads
    constructor(context: Context, attrs: AttributeSet? = null, style: Int = 0):
        super(context, attrs, style)

    override fun getLayoutResource(): Int {
        return R.layout.view_balance
    }

    fun setBalance(homeBalanceState: HomePresenter.HomeBalanceState) {
        val paymentContext = homeBalanceState.paymentContext

        this.rateProvider = ExchangeRateProvider(paymentContext.exchangeRateWindow.rates)
        this.balanceInSatoshis = paymentContext.userBalance
        this.mode = homeBalanceState.currencyDisplayMode

        this.primaryCurrency = paymentContext.user
            .getPrimaryCurrency(paymentContext.exchangeRateWindow)

        this.clockState = homeBalanceState.utxoSetState

        clockIcon.visibility = if (clockState == UtxoSetStateSelector.UtxoSetState.CONFIRMED) View.GONE else View.VISIBLE

        if (clockState == UtxoSetStateSelector.UtxoSetState.PENDING || clockState == UtxoSetStateSelector.UtxoSetState.RBF) {
            DrawableCompat.setTint(clockDrawable, getColor(clockState))
            clockIcon.setImageDrawable(clockDrawable)
        }

        this.hidden = homeBalanceState.balanceHidden
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
        mainAmount.text = BitcoinHelper.formatShortBitcoinAmount(balanceInSatoshis, false, mode)
        mainCurrencyCode.text = MoneyHelper.formatCurrency(Currency.BTC.code, mode)

        if (!MoneyHelper.isBtc(primaryCurrency)) {
            val balance = BitcoinUtils.satoshisToBitcoins(balanceInSatoshis)
            val balanceInPrimaryCurr = rateProvider!!.convert(balance, primaryCurrency)
            secondaryAmount.text = MoneyHelper.formatLongMonetaryAmount(balanceInPrimaryCurr, mode)

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