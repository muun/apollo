package io.muun.apollo.domain.model

import io.muun.apollo.domain.utils.FeeCalculator
import io.muun.common.model.ExchangeRateProvider
import io.muun.common.utils.BitcoinUtils
import io.muun.common.utils.Preconditions
import org.javamoney.moneta.Money
import java.util.*
import javax.money.CurrencyUnit
import javax.money.Monetary
import javax.money.MonetaryAmount

/**
 * The contextual information required to analyze and process a PaymentRequest.
 */
class PaymentContext(
    val user: User,
    val exchangeRateWindow: ExchangeRateWindow,
    val feeWindow: FeeWindow,
    val nextTransactionSize: NextTransactionSize
) {

    companion object {
        private const val AVG_MS_PER_BLOCK = 10 * (60 * 1000)

        // NOTE: this is a hack to ensure all operation-related screens share a preset payment
        // context. Ideally, NewOperation would be an Activity with Fragments sharing this.
        var currentlyInUse: PaymentContext? = null
    }

    private val rateProvider = ExchangeRateProvider(exchangeRateWindow.rates)

    /** The total balance in the wallet, independent of fees */
    val totalBalance =
        nextTransactionSize.maxAmountInSatoshis

    /** The recommended fee rates for the user to pick */
    private val feeOptions = feeWindow.targetedFees.mapValues { (confTarget, satoshisPerByte) ->
        val feeCalculator = FeeCalculator(satoshisPerByte, nextTransactionSize.sizeProgression)

        FeeOption(
            satoshisPerByte = satoshisPerByte,
            confirmationTarget = confTarget,
            minTimeMs = estimateMinTimeMs(confTarget),
            maxTimeMs = estimateMaxTimeMs(confTarget),
            feeCalculator = feeCalculator
        )
    }.toSortedMap()

    /**
     * Get the fee option with the minimum available fee rate that will hit a given confirmation
     * target. We make no guesses (no averages or interpolations), so we might overshoot the fee
     * if data is too sparse.
     * Note: the lower the confirmation target, the faster the tx will confirm, and greater the
     * fee(rate) will be.
     * Assumes feeOptions is a SortedMap sorted in ascending order, and has at least one item.
     */
    fun closestFeeOptionFasterThan(confirmationTarget: Int): FeeOption {
        Preconditions.checkPositive(confirmationTarget)

        for (closestTarget in confirmationTarget downTo 1) {
            if (feeOptions.containsKey(closestTarget)) {
                // Found! This is the lowest fee rate that hits the given target.
                return feeOptions[closestTarget]!!
            }
        }

        // No result? This is odd, but not illogical. It means *all* of our available targets
        // are above the requested one. Let's use the fastest:
        val lowestTarget = feeOptions.firstKey()
        return feeOptions[lowestTarget]!!
    }

    /**
     * Assumes feeOptions is a SortedMap sorted in ascending order, and has at least one item.
     */
    fun estimateMaxTimeMsFor(feeRate: Double): Long {

        for (feeOption in feeOptions.values) {
            if (feeOption.satoshisPerByte <= feeRate) {
                return feeOption.maxTimeMs
            }
        }

        return feeOptions[feeOptions.lastKey()]!!.maxTimeMs
    }

    /**
     * Assumes feeOptions is a SortedMap sorted in ascending order, and has at least one item.
     */
    fun getRandomFee(): FeeOption {
        var maxConfirmationTarget = feeOptions[feeOptions.lastKey()]!!.confirmationTarget
        return closestFeeOptionFasterThan(Random().nextInt(maxConfirmationTarget + 1))
    }

    /**
     * Examine a PaymentRequest, and return a PaymentAnalysis.
     */
    fun analyze(payReq: PaymentRequest): PaymentAnalysis {
        checkNotNull(payReq.amount)
        checkNotNull(payReq.feeInSatoshisPerByte)

        return PaymentAnalyzer(this, payReq).analyze()
    }

    /**
     * Take a valid PaymentRequest (verify using `analyze`), and create a PreparedPayment.
     */
    fun prepare(payReq: PaymentRequest): PreparedPayment {
        val analysis = analyze(payReq)

        check(analysis.isValid())
        checkNotNull(analysis.fee) // these two are implied, but we hint the compiler
        checkNotNull(analysis.total)

        return PreparedPayment(
            analysis.amount,
            analysis.fee,
            analysis.sweepFee,
            analysis.total,
            analysis.payReq.description,
            analysis.rateWindowHid
        )
    }

    /**
     * Convert a MonetaryAmount to Satoshis.
     */
    fun convertToSatoshis(amount: MonetaryAmount) =
        convert(amount, Monetary.getCurrency("BTC")).let(BitcoinUtils::bitcoinsToSatoshis)

    /**
     * Convert satoshis to a MonetaryAmount.
     */
    fun convert(amountInSatoshis: Long, targetUnit: CurrencyUnit) =
        convert(convertToBitcoin(amountInSatoshis), targetUnit)

    /**
     * Convert satoshis to a MonetaryAmount in BTC.
     */
    fun convertToBitcoin(amountInSatoshis: Long) =
        Money.of(amountInSatoshis, "BTC").scaleByPowerOfTen(-BitcoinUtils.BITCOIN_PRECISION)

    /**
     * Convert a MonetaryAmount to another currency.
     */
    fun convert(amount: MonetaryAmount, targetUnit: CurrencyUnit) =
        amount.with(rateProvider.getCurrencyConversion(targetUnit))


    /**
     * Convert satoshis to a complex BitcoinAmount.
     */
    fun convertToBitcoinAmount(amountInSatoshis: Long, inputCurrency: CurrencyUnit) =
        BitcoinAmount(
            amountInSatoshis,
            convert(amountInSatoshis, inputCurrency),
            convert(amountInSatoshis, user.primaryCurrency)
        )

    private fun estimateMinTimeMs(confTarget: Int) =
        confTarget.toLong() * AVG_MS_PER_BLOCK / 2

    private fun estimateMaxTimeMs(confTarget: Int) =
        confTarget.toLong() * AVG_MS_PER_BLOCK * 2
}
