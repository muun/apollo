package io.muun.apollo.domain.utils

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.muun.apollo.data.external.Gen
import io.muun.apollo.data.external.Globals
import io.muun.apollo.domain.libwallet.Invoice
import io.muun.apollo.domain.model.ExchangeRateWindow
import io.muun.apollo.domain.model.FeeWindow
import io.muun.apollo.domain.model.NextTransactionSize
import io.muun.apollo.domain.model.PaymentAnalysis
import io.muun.apollo.domain.model.PaymentContext
import io.muun.apollo.domain.model.PaymentRequest
import io.muun.apollo.domain.model.SubmarineSwapFundingOutputPolicies
import io.muun.apollo.domain.model.User
import io.muun.common.utils.Preconditions
import org.assertj.core.api.Assertions.assertThat
import org.javamoney.moneta.Money
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import javax.money.Monetary
import kotlin.random.Random

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see [Testing documentation](http://d.android.com/tools/testing)
 */
@RunWith(AndroidJUnit4::class)
class PaymentAnalyzerTest {

    private val USD = Monetary.getCurrency("USD")

    lateinit var user: User
    lateinit var exchangeRateWindow: ExchangeRateWindow
    lateinit var feeWindow: FeeWindow

    @Before
    fun setUp() {
        // Defaults:
        user = Gen.user(primaryCurrency = USD)
        exchangeRateWindow = Gen.exchangeRateWindow()
        feeWindow = Gen.feeWindow(
            1 to 100.0,
            5 to 50.0,
            10 to 0.25
        )
    }

    @Test
    @Ignore("Must be run manually")
    fun fuzzSwaps() {
        for (i in 1..200000) {
            Log.i("", i.toString())
            runSwapTest()
        }
    }

    @Test
    @Ignore("Must be run manually")
    fun fuzzOnChain() {
        for (i in 1..200000) {
            Log.i("", i.toString())
            runOnchainTest()
        }
    }

    @Test
    fun failure1() {
        run(proportionalFee = 1051,
            baseFee = 4,
            maxDebt = 123124,
            potentialCollect = 20686,
            maxFor0Conf = 193437,
            amount = 10055,
            useAllFunds = true,
            nts = Gen.nextTransactionSize(
                Gen.sizeForAmount(36931L to 253),
                expectedDebtInSat = 26876
            )
        )
    }

    @Test
    fun failure2() {
        run(proportionalFee = 297,
            baseFee = 4,
            maxDebt = 76410,
            potentialCollect = 33292,
            maxFor0Conf = 254235,
            amount = 10290,
            useAllFunds = true,
            nts = Gen.nextTransactionSize(
                Gen.sizeForAmount(83880L to 495),
                expectedDebtInSat = 73590
            )
        )
    }

    @Test
    fun failure3() {
        run(proportionalFee = 875,
            baseFee = 2,
            maxDebt = 148489,
            potentialCollect = 759,
            maxFor0Conf = 163704,
            amount = 86,
            useAllFunds = true,
            nts = Gen.nextTransactionSize(
                Gen.sizeForAmount(1597L to 391),
                expectedDebtInSat = 1511
            )
        )
    }

    @Test
    fun failure4() {
        run(proportionalFee = 468,
            baseFee = 4,
            maxDebt = 149224,
            potentialCollect = 246,
            maxFor0Conf = 243320,
            amount = 240,
            useAllFunds = true,
            nts = Gen.nextTransactionSize(
                Gen.sizeForAmount(1016L to 409),
                expectedDebtInSat = 776
            )
        )
    }

    @Test
    fun failure5() {
        run(proportionalFee = 1417,
            baseFee = 2,
            maxDebt = 149542,
            potentialCollect = 222,
            maxFor0Conf = 223421,
            amount = 1385,
            useAllFunds = true,
            nts = Gen.nextTransactionSize(
                Gen.sizeForAmount(1843L to 475),
                expectedDebtInSat = 458
            )
        )
    }

    @Test
    fun failure6() {
        run(proportionalFee = 1991,
            baseFee = 1,
            maxDebt = 143461,
            potentialCollect = 6422,
            maxFor0Conf = 254856,
            amount = 119,
            useAllFunds = true,
            nts = Gen.nextTransactionSize(
                Gen.sizeForAmount(6658L to 262),
                expectedDebtInSat = 6539
            )
        )
    }

    @Test
    fun failure7() {
        run(proportionalFee = 1009,
            baseFee = 2,
            maxDebt = 144381,
            potentialCollect = 1140,
            maxFor0Conf = 210617,
            amount = 2550,
            useAllFunds = true,
            nts = Gen.nextTransactionSize(
                Gen.sizeForAmount(8169L to 301), expectedDebtInSat = 5619
            )
        )
    }

    @Test
    fun failure8() {
        run(proportionalFee = 655,
            baseFee = 1,
            maxDebt = 109529,
            potentialCollect = 13892,
            maxFor0Conf = 258399,
            amount = 59477,
            useAllFunds = true,
            nts = Gen.nextTransactionSize(
                Gen.sizeForAmount(46785L to 365),
                Gen.sizeForAmount(99948L to 857),
                expectedDebtInSat = 40471
            )
        )

    }

    @Test
    fun failure9() {
        run(proportionalFee = 1146,
            baseFee = 3,
            maxDebt = 65883,
            potentialCollect = 77393,
            maxFor0Conf = 240440,
            amount = 72667,
            useAllFunds = true,
            nts = Gen.nextTransactionSize(
                Gen.sizeForAmount(10087L to 430),
                Gen.sizeForAmount(156784L to 851),
                expectedDebtInSat = 84117
            )
        )
    }

    @Test
    fun failure10() {
        run(proportionalFee = 890,
            baseFee = 1,
            maxDebt = 131775,
            potentialCollect = 16109,
            maxFor0Conf = 182743,
            amount = 98972,
            useAllFunds = true,
            nts = Gen.nextTransactionSize(
                Gen.sizeForAmount(117197L to 333),
                expectedDebtInSat = 18225
            )
        )
    }

    @Test
    fun failure11() {
        run(proportionalFee = 1461,
            baseFee = 3,
            maxDebt = 147188,
            potentialCollect = 222,
            maxFor0Conf = 164563,
            amount = 353,
            useAllFunds = true,
            nts = Gen.nextTransactionSize(
                Gen.sizeForAmount(3165L to 402),
                expectedDebtInSat = 2812
            )
        )
    }

    @Test
    fun failure12() {
        run(proportionalFee = 1457,
            baseFee = 2,
            maxDebt = 117361,
            potentialCollect = 27161,
            maxFor0Conf = 287685,
            amount = 53704,
            useAllFunds = true,
            nts = Gen.nextTransactionSize(
                Gen.sizeForAmount(86343L to 358),
                expectedDebtInSat = 32639
            )
        )
    }

    @Test
    fun failure13() {
        run(
            proportionalFee = 1000,
            baseFee = 1,
            maxDebt = 0,
            potentialCollect = 0,
            maxFor0Conf = Long.MAX_VALUE,
            amount = 500,
            useAllFunds = true,
            nts = Gen.nextTransactionSize(
                Gen.sizeForAmount(500L to 209),
                expectedDebtInSat = 0
            )
        )
    }

    @Test
    fun zeroAmountIsTooSmall() {
        val result = run(
            proportionalFee = 1000,
            baseFee = 0,
            maxDebt = 0,
            potentialCollect = 0,
            maxFor0Conf = Long.MAX_VALUE,
            amount = 0,
            useAllFunds = true,
            nts = Gen.nextTransactionSize(
                Gen.sizeForAmount(600L to 209),
                expectedDebtInSat = 0
            )
        )

        assertThat(result.isAmountTooSmall).isTrue()
    }

    fun runOnchainTest() {

        val useAllfunds = Random.nextBoolean()

        var totalAmount = 0L
        var totalSize = 0
        val ntsEntries = Array(Random.nextInt(1, 5)) {
            totalAmount += Random.nextLong(1000, 1_500_000)
            totalSize += Random.nextInt(209, 500)
            Gen.sizeForAmount(
                totalAmount to totalSize
            )
        }
        // Don't do 0 balance through debt, it's a boring case to fuzz
        val expectedDebtInSat = Random.nextLong(0, totalAmount - 1)
        val nts = Gen.nextTransactionSize(
            *ntsEntries, expectedDebtInSat = expectedDebtInSat
        )

        val amount = if (useAllfunds)
            nts.userBalance
        else
            Random.nextLong(1, nts.userBalance)

        runForFuzz(
            proportionalFee = null,
            baseFee = null,
            maxDebt = null,
            maxFor0Conf = null,
            potentialCollect = null,
            amount = amount,
            useAllFunds = useAllfunds,
            nts = nts
        )
    }

    fun runSwapTest() {

        val baseFee = Random.nextLong(1, 5)
        val proportionalFee = Random.nextLong(100, 2000)
        val maxFor0Conf = Random.nextLong(150_000, 300_000)
        val useAllfunds = Random.nextBoolean()

        var totalAmount = 0L
        var totalSize = 0
        val ntsEntries = Array(Random.nextInt(1, 3)) {
            totalAmount += Random.nextLong(1000, 150_000)
            totalSize += Random.nextInt(209, 500)
            Gen.sizeForAmount(
                totalAmount to totalSize
            )
        }
        // Don't do 0 balance through debt, it's a boring case to fuzz
        val expectedDebtInSat = Random.nextLong(0, totalAmount - 1)
        val nts = Gen.nextTransactionSize(
            *ntsEntries, expectedDebtInSat = expectedDebtInSat
        )

        val maxDebt = 150_000L - nts.expectedDebtInSat
        val potentialCollect = if (nts.expectedDebtInSat > 0)
            Random.nextLong(0, nts.expectedDebtInSat)
        else
            0

        val amount = if (useAllfunds)
            nts.userBalance
        else
            Random.nextLong(1, nts.userBalance)

        runForFuzz(
            proportionalFee = proportionalFee,
            baseFee = baseFee,
            maxDebt = maxDebt,
            potentialCollect = potentialCollect,
            maxFor0Conf = maxFor0Conf,
            amount = amount,
            useAllFunds = useAllfunds,
            nts = nts
        )
    }


    private fun run(proportionalFee: Long?,
                    baseFee: Long?,
                    maxDebt: Long?,
                    potentialCollect: Long?,
                    maxFor0Conf: Long?,
                    amount: Long,
                    useAllFunds: Boolean,
                    nts: NextTransactionSize): PaymentAnalysis {

        val ctx = PaymentContext(user, exchangeRateWindow, feeWindow, nts)

        val payReq: PaymentRequest
        if (proportionalFee != null) {
            checkNotNull(proportionalFee)
            checkNotNull(baseFee)
            checkNotNull(maxDebt)
            checkNotNull(potentialCollect)
            checkNotNull(maxFor0Conf)

            val swap = Gen.amountlessSubmarineSwap(
                proportionalFee,
                baseFee,
                maxDebt,
                potentialCollect,
                maxFor0Conf
            )

            val invoice = Invoice.decodeInvoice(Globals.INSTANCE.network, swap.invoice)
            payReq = PaymentRequest(
                type = PaymentRequest.Type.TO_LN_INVOICE,
                amount = amount.let<Long, Money?>(ctx::convertToBitcoin),
                description = "Some swap",
                invoice = invoice,
                swap = swap,
                feeInSatoshisPerByte = null,
                takeFeeFromAmount = useAllFunds
            )

        } else {
            payReq = PaymentRequest(
                type = PaymentRequest.Type.TO_ADDRESS,
                amount = amount.let<Long, Money?>(ctx::convertToBitcoin),
                description = "Some address",
                feeInSatoshisPerByte = feeWindow.fastestFeeInSatoshisPerByte,
                takeFeeFromAmount = useAllFunds
            )
        }

        val analyze = checkNotNull(ctx.analyze(payReq))
        if (analyze.canPayWithSelectedFee && payReq.swap != null) {
            matchToSwapperLogic(analyze, payReq.swap!!.fundingOutputPolicies!!)
        }

        val _unused = analyze.isAmountTooSmall

        Preconditions.checkNonNegative(analyze.amount.inSatoshis)
        Preconditions.checkNonNegative(analyze.outputAmount.inSatoshis)
        analyze.fee?.inSatoshis?.let { Preconditions.checkNonNegative(it) }
        analyze.total?.inSatoshis?.let { Preconditions.checkNonNegative(it) }

        if (analyze.canPayWithSelectedFee) {
            checkNotNull(analyze.fee)
            checkNotNull(analyze.total)
        }

        return analyze
    }

    private fun runForFuzz(proportionalFee: Long?,
                           baseFee: Long?,
                           maxDebt: Long?,
                           potentialCollect: Long?,
                           maxFor0Conf: Long?,
                           amount: Long,
                           useAllFunds: Boolean,
                           nts: NextTransactionSize) {

        try {
            run(
                proportionalFee,
                baseFee,
                maxDebt,
                potentialCollect,
                maxFor0Conf,
                amount,
                useAllFunds,
                nts
            )

        } catch (e: Throwable) {

            Log.i("FUZZ", "Failed test case:" +
                "run(" +
                " proportionalFee = ${proportionalFee}," +
                " baseFee = ${baseFee}," +
                " maxDebt = ${maxDebt}," +
                " potentialCollect = ${potentialCollect}," +
                " maxFor0Conf = ${maxFor0Conf}," +
                " amount = ${amount}," +
                " useAllFunds = ${useAllFunds}," +
                " nts = Gen.nextTransactionSize(" +
                nts.sizeProgression.map {
                    "Gen.sizeForAmount(${it.amountInSatoshis}L to ${it.sizeInBytes})"
                }.joinToString(",") +
                ", expectedDebtInSat = ${nts.expectedDebtInSat}" +
                "))\n ${e.javaClass.simpleName}: ${e.message}"
            )
        }
    }

    private fun matchToSwapperLogic(
        paymentAnalysis: PaymentAnalysis,
        policies: SubmarineSwapFundingOutputPolicies
    ) {
        val swapperFundingOutputPolicies = SwapperFundingOutputPolicies(
            policies.maximumDebtInSat,
            policies.potentialCollectInSat,
            policies.maxAmountInSatFor0Conf
        )

        val amount = paymentAnalysis.amount.inSatoshis
        val lightningFee = paymentAnalysis.lightningFee!!.inSatoshis
        val fundingOutput = paymentAnalysis.payReq.swap!!.fundingOutput
        val hasTx = paymentAnalysis.hasOnChainTransaction

        assertThat(fundingOutput.debtType)
            .`as`("debtType")
            .isEqualTo(
                swapperFundingOutputPolicies.getDebtType(amount, lightningFee, hasTx)
            )

        assertThat(fundingOutput.debtAmountInSatoshis)
            .`as`("debtAmount")
            .isEqualTo(
                swapperFundingOutputPolicies.getDebtAmount(amount, lightningFee, hasTx).toSats()
            )

        assertThat(fundingOutput.confirmationsNeeded)
            .`as`("confirmationsNeeded")
            .isEqualTo(
                swapperFundingOutputPolicies.getFundingConfirmations(amount, lightningFee)
            )

        assertThat(fundingOutput.outputAmountInSatoshis)
            .`as`("outputAmountInSatoshis")
            .isEqualTo(
                swapperFundingOutputPolicies.getFundingOutputAmount(amount, lightningFee, hasTx)
                    .toSats()
            )
    }

}