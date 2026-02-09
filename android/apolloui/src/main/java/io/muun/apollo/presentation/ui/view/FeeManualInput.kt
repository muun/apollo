package io.muun.apollo.presentation.ui.view

import android.content.Context
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.View
import androidx.viewbinding.ViewBinding
import io.muun.apollo.R
import io.muun.apollo.databinding.ManualFeeInputBinding
import io.muun.apollo.domain.ApplicationLockManager
import io.muun.apollo.domain.errors.LocaleNumberParsingError
import io.muun.apollo.domain.model.BitcoinAmount
import io.muun.apollo.domain.model.BitcoinUnit
import io.muun.apollo.presentation.ui.helper.BitcoinHelper
import io.muun.apollo.presentation.ui.helper.MoneyHelper
import io.muun.apollo.presentation.ui.helper.isBtc
import io.muun.apollo.presentation.ui.utils.OS
import io.muun.apollo.presentation.ui.utils.UiUtils
import io.muun.apollo.presentation.ui.utils.normalizeDecimalInput
import io.muun.common.utils.Dates
import io.muun.common.utils.Preconditions
import timber.log.Timber
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.text.ParseException
import java.util.Locale
import javax.inject.Inject
import javax.money.MonetaryAmount

class FeeManualInput @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : MuunView(context, attrs, defStyleAttr) {

    private val binding: ManualFeeInputBinding
        get() = getBinding() as ManualFeeInputBinding

    override fun viewBinder(): ((View) -> ViewBinding) {
        return ManualFeeInputBinding::bind
    }

    @Inject
    lateinit var lockManager: ApplicationLockManager

    private var onChangeListener: ((feeRateInSatsPerVbyte: Double?) -> Unit)? = null

    override val layoutResource: Int
        get() = R.layout.manual_fee_input

    override fun setUp(context: Context, attrs: AttributeSet?) {
        super.setUp(context, attrs)
        component?.inject(this)

        setUpUi()
    }

    private fun setUpUi() {
        binding.feeInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable) {
                notifyChange(parseNumber(s.toString()))
            }
        })
    }

    /**
     * Focus on this FeeManualInput and show the soft keyboard.
     * Our own version of [View.requestFocus] but renamed since that one is final and we
     * can't override it.
     */
    fun requestFocusInput() {
        if (!lockManager.isLockSet) {
            UiUtils.focusInput(binding.feeInput) // Don't show soft keyboard if lock screen's
            // showing
        }
    }

    /**
     * Reset widget to initial state.
     */
    fun resetVisibility() {
        binding.feeEstimatedTime.visibility = INVISIBLE
        binding.feeMainValue.visibility = INVISIBLE
        binding.feeSecondaryValue.visibility = INVISIBLE
    }

    /**
     * Set estimation for maximum confirmation time, for this fee option.
     */
    fun setMaxTimeMs(timeInMillis: Long) {
        // TODO abstract this logic (also in InvoiceExpirationCountdownTimer)

        val timeInSeconds = timeInMillis / 1000

        val hours = timeInSeconds / Dates.HOUR_IN_SECONDS
        val minutes = (timeInSeconds % Dates.HOUR_IN_SECONDS) / Dates.MINUTE_IN_SECONDS

        val timeText = if (hours > 0) {
            context.getString(R.string.fee_option_item_hs, hours)
        } else {
            context.getString(R.string.fee_option_item_mins, minutes)
        }

        val text = TextUtils.concat(
            getResources().getString(R.string.fee_option_item_title),
            " ",
            RichText(timeText).setBold()
        )

        binding.feeEstimatedTime.text = text
        binding.feeEstimatedTime.visibility = VISIBLE
    }

    /**
     * Set nominal fee value, for this fee option.
     */
    fun setFee(fee: BitcoinAmount?, bitcoinUnit: BitcoinUnit) {
        Preconditions.checkNotNull<BitcoinAmount?>(fee)
        requireNotNull(fee) // Shouldn't reach here without this

        setFeeInBtc(fee.inSatoshis, bitcoinUnit)

        // Don't show fee in btc twice! If input currency is btc, show fee in primary currency
        if (fee.inInputCurrency.isBtc()) {
            setFeeInSecondaryCurrency(fee.inPrimaryCurrency, bitcoinUnit)
        } else {
            setFeeInSecondaryCurrency(fee.inInputCurrency, bitcoinUnit)
        }

        binding.feeMainValue.visibility = VISIBLE
        binding.feeSecondaryValue.visibility = VISIBLE
    }

    private fun setFeeInBtc(feeInSat: Long, bitcoinUnit: BitcoinUnit) {
        binding.feeMainValue.text =
            BitcoinHelper.formatLongBitcoinAmount(feeInSat, bitcoinUnit, locale)
    }

    /**
     * Set fee value, in secondary currency, for this fee option.
     */
    private fun setFeeInSecondaryCurrency(feeAmount: MonetaryAmount, bitcoinUnit: BitcoinUnit) {
        binding.feeSecondaryValue.text = TextUtils.concat(
            "(",
            MoneyHelper.formatLongMonetaryAmount(feeAmount, bitcoinUnit, locale),
            ")"
        )
    }

    fun setOnChangeListener(onChangeListener: (feeRateInSatsPerVbyte: Double?) -> Unit) {
        this.onChangeListener = onChangeListener
    }

    private fun notifyChange(fee: Double?) {
        onChangeListener?.invoke(fee)
    }

    private fun parseNumber(input: String): Double? {
        if (input.isEmpty()) {
            return null
        }

        val locale = if (OS.supportsLocales()) {
            // Obtain only main locale
            resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            resources.configuration.locale
        }
        val normalizedInput = normalizeDecimalInput(input, locale)

        return try {
            NumberFormat.getInstance(locale).parse(normalizedInput)?.toDouble()
        } catch (e: ParseException) {
            // Only log if it's effectively a parse error (empty str can't be parsed into a double)
            if (DecimalFormatSymbols(locale).decimalSeparator.toString() != input) {
                Timber.e(LocaleNumberParsingError(input, locale, e))
            }
            null
        }
    }
}
