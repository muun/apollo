package io.muun.apollo.utils.screens

import android.content.Context
import androidx.annotation.IdRes
import androidx.test.uiautomator.UiDevice
import io.muun.apollo.R
import io.muun.apollo.utils.WithMuunInstrumentationHelpers
import javax.money.MonetaryAmount

class RecommendedFeeScreen(
    override val device: UiDevice,
    override val context: Context
): WithMuunInstrumentationHelpers {

    inner class OnScreenFeeOption(
        val feeRate: Double,
        val primaryAmount: MonetaryAmount,
        val secondaryAmount: MonetaryAmount
    )

    fun selectFeeOptionFast() =
        readFeeOptionFast().also { id(R.id.fee_option_fast).click() }

    fun selectFeeOptionMedium() =
        readFeeOptionMedium().also { id(R.id.fee_option_medium).click() }

    fun selectFeeOptionSlow() =
        readFeeOptionSlow().also { id(R.id.fee_option_slow).click() }

    fun hasFeeOptionSlow() =
        id(R.id.fee_option_slow).exists()

    fun confirmFee() =
        pressMuunButton(R.id.confirm_fee)

    fun goToManualFee() =
        id(R.id.enter_fee_manually).click()

    private fun readFeeOptionFast() =
        readFeeOption(R.id.fee_option_fast)

    private fun readFeeOptionMedium() =
        readFeeOption(R.id.fee_option_medium)

    private fun readFeeOptionSlow() =
        readFeeOption(R.id.fee_option_slow)

    private fun readFeeOption(@IdRes resId: Int) =
        id(resId).let {
            OnScreenFeeOption(
                it.child(R.id.fee_option_fee_rate).text.dropUnit().toDouble(),
                it.child(R.id.fee_option_main_value).text.toMoney(),
                it.child(R.id.fee_option_secondary_value).text.dropParenthesis().toMoney()
            )
        }
}