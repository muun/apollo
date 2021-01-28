package io.muun.apollo.utils.screens

import android.content.Context
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import io.muun.apollo.R
import io.muun.apollo.utils.WithMuunInstrumentationHelpers
import org.assertj.core.api.Assertions.assertThat
import org.junit.ComparisonFailure
import javax.money.MonetaryAmount

class OperationDetailScreen(
    override val device: UiDevice,
    override val context: Context
): WithMuunInstrumentationHelpers {

    fun waitForStatusChange(@StringRes stringResId: Int) {
        waitForStatusChange(context.getString(stringResId))
    }

    fun waitForStatusChange(statusText: String) {
        scrollToFind(detailItemTitle(R.id.operation_detail_status))
        maybeDetailItemTitle(R.id.operation_detail_status)
            .wait(Until.textContains(statusText), 15000)
    }

    fun checkStatus(vararg statusTexts: String) {

        for (index in (0 until (statusTexts.size - 1))) {
            try {
                checkStatus(statusTexts[index])
                return
            } catch (e: ComparisonFailure) {
                // ignore until last
            }
        }

        // If every check failed we want the last to raise an error and fail the test
        checkStatus(statusTexts[statusTexts.size - 1])
    }

    fun checkStatus(statusText: String) {
        assertThat(detailItemTitle(R.id.operation_detail_status).text)
            .isEqualTo(statusText)
    }

    fun checkDescription(description: String) {
        assertThat(detailItemContent(R.id.operation_detail_description).text)
            .contains(description)
    }

    fun checkAmount(amount: MonetaryAmount) {
        checkMonetaryAmounItem(R.id.operation_detail_amount, amount)
    }

    fun checkFee(fee: MonetaryAmount) {
        checkMonetaryAmounItem(R.id.operation_detail_fee, fee)
    }

    fun checkLightningFee(lightningFee: MonetaryAmount) {
        checkMonetaryAmounItem(R.id.operation_detail_fee, lightningFee)
    }

    fun checkInvoice(invoice: String) {
        checkItemContent(R.id.operation_detail_swap_invoice, invoice)
    }

    /**
     * Note this method only works with BTC amounts.
     * We do a "fuzzy" check by checking that the DetailItemContent startsWith the specified amount,
     * since detail item amounts also have appended the amount in primary currency (which we can't
     * get from newOp screen).
     */
    private fun checkMonetaryAmounItem(id: Int, amount: MonetaryAmount) {
        assertThat(amount.currency.currencyCode).isEqualTo("BTC")

        val item = detailItemContent(id)
        scrollToFind(item)
        assertThat(item.text).startsWith(amount.toText())
    }

    private fun checkItemContent(@IdRes id: Int, expectedContent: String) {
        val item = detailItemContent(id)
        scrollToFind(item)
        assertThat(item.text).isEqualTo(expectedContent)
    }
}
