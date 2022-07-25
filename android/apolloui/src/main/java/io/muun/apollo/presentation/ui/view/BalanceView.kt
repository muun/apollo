package io.muun.apollo.presentation.ui.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.drawable.DrawableCompat
import butterknife.BindColor
import butterknife.BindDrawable
import butterknife.BindView
import icepick.State
import io.muun.apollo.R
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
import kotlin.math.roundToInt

class BalanceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    style: Int = 0,
) : MuunView(context, attrs, style) {

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

    private val originalAmountTextSizePx = mainAmount.textSize

    private val originalCurrencyTextSizePx = mainCurrencyCode.textSize

    override val layoutResource: Int
        get() = R.layout.view_balance

    fun setBalance(homeState: HomeFragmentPresenter.HomeState) {
        val paymentContext = homeState.paymentContext

        this.rateProvider = ExchangeRateProvider(paymentContext.exchangeRateWindow.toJson())
        this.balanceInSatoshis = paymentContext.userBalance
        this.bitcoinUnit = homeState.bitcoinUnit

        this.primaryCurrency = paymentContext.user
            .getPrimaryCurrency(paymentContext.exchangeRateWindow)

        this.clockState = homeState.utxoSetState

        clockIcon.visibility = if (clockState == UtxoSetStateSelector.UtxoSetState.CONFIRMED) {
            View.GONE
        } else {
            View.VISIBLE
        }

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

        resizeTextFields()
    }

    private fun resizeTextFields() {
        val (mainAmountSize, mainCurrencySize) = calculateSizes()
        mainAmount.setTextSize(TypedValue.COMPLEX_UNIT_PX, mainAmountSize)
        mainCurrencyCode.setTextSize(TypedValue.COMPLEX_UNIT_PX, mainCurrencySize)
    }

    private fun calculateSizes(): Pair<Float, Float> {
        val maxWidthPx = (this.parent as View).measuredWidth
        var amountTextSizePx = originalAmountTextSizePx
        var currencyTextSizePx = originalCurrencyTextSizePx

        var width: Float = getEstimatedWidth(originalAmountTextSizePx, originalCurrencyTextSizePx)

        if (width < maxWidthPx) {
            return Pair(originalAmountTextSizePx, originalCurrencyTextSizePx)
        }

        while (width > maxWidthPx) {
            amountTextSizePx--

            val resizeRatio = amountTextSizePx / originalAmountTextSizePx
            currencyTextSizePx = (originalCurrencyTextSizePx * resizeRatio)
            width = getEstimatedWidth(amountTextSizePx, currencyTextSizePx)
        }
        return Pair(amountTextSizePx, currencyTextSizePx)
    }

    /**
     * Returns the estimated width of the line formed by the amount, currency and clock image,
     * considering parent paddings and space between components.
     */
    private fun getEstimatedWidth(amountTextSizePx: Float, currencyTextSizePx: Float): Float {
        val paintAmount = TextPaint()
        val paintCurrency = TextPaint()
        paintAmount.textSize = amountTextSizePx
        paintCurrency.textSize = currencyTextSizePx
        paintAmount.typeface = mainAmount.typeface
        paintCurrency.typeface = mainCurrencyCode.typeface
        return paintAmount.measureText(mainAmount.text as String) +
            paintCurrency.measureText(mainCurrencyCode.text as String) +
            UiUtils.dpToPx(context, clockIcon.drawable.minimumWidth) +
            resources.getDimensionPixelSize(R.dimen.home_fragment_padding) +
            resources.getDimensionPixelSize(R.dimen.home_fragment_padding) +
            resources.getDimensionPixelSize(R.dimen.balance_clock_icon_margin_end) +
            resources.getDimensionPixelSize(R.dimen.balance_main_currency_code_margin_start) +
            // extra margin just in case, so we always avoid text clipping
            resources.getDimensionPixelSize(R.dimen.balance_view_extra)
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
