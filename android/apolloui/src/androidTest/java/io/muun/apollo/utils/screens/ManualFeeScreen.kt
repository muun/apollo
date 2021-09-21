package io.muun.apollo.utils.screens

import android.content.Context
import androidx.test.uiautomator.UiDevice
import io.muun.apollo.R
import io.muun.apollo.domain.utils.locale
import io.muun.apollo.utils.WithMuunInstrumentationHelpers
import javax.money.MonetaryAmount

class ManualFeeScreen(
    override val device: UiDevice,
    override val context: Context
): WithMuunInstrumentationHelpers {

    inner class OnScreenFeeOption(
        val feeRate: Double,
        val primaryAmount: MonetaryAmount,
        val secondaryAmount: MonetaryAmount
    )

    fun editFeeRate(feeRate: Double): OnScreenFeeOption {
        feeInput.text = feeRate.toString()
        return feeOption
    }

    fun confirmFeeRate() {
        pressMuunButton(R.id.confirm_fee)
    }

    private val feeOption get() =
        OnScreenFeeOption(
            feeInput.text.parseDecimal(locale),
            id(R.id.fee_main_value).text.toMoney(context.locale()),
            id(R.id.fee_secondary_value).text.dropParenthesis().toMoney(locale)
        )

    private val feeInput get() =
        id(R.id.fee_manual_input).child(R.id.fee_input)
}