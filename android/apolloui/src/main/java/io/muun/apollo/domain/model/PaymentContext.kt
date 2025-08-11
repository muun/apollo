package io.muun.apollo.domain.model

import io.muun.apollo.domain.model.user.User
import io.muun.common.Rules
import newop.InitialPaymentContext

/**
 * The contextual information required to analyze and process a PaymentRequest.
 */
class PaymentContext(
    val user: User,
    val exchangeRateWindow: ExchangeRateWindow,
    val feeWindow: FeeWindow,
    val nextTransactionSize: NextTransactionSize,
    private val minFeeRate: Double = Rules.OP_MINIMUM_FEE_RATE
) {

    /** The total UI balance in the wallet, independent of fees, as calculated by NTS */
    val userBalance = nextTransactionSize.userBalance

    /**
     * Adapt apollo's (java) model to libwallet's (go).
     */
    fun toLibwallet(submarineSwap: SubmarineSwap?): InitialPaymentContext {
        val libwalletPayCtx = InitialPaymentContext()
        libwalletPayCtx.feeWindow = feeWindow.toLibwallet()
        libwalletPayCtx.exchangeRateWindow = exchangeRateWindow.toLibwallet()
        libwalletPayCtx.nextTransactionSize = nextTransactionSize.toLibwallet()
        libwalletPayCtx.primaryCurrency = user.getPrimaryCurrency(exchangeRateWindow).currencyCode
        libwalletPayCtx.minFeeRateInSatsPerVByte = Rules.toSatsPerVbyte(minFeeRate)
        if (submarineSwap != null) {
            libwalletPayCtx.submarineSwap = submarineSwap.toLibwallet()
        }
        return libwalletPayCtx
    }
}
