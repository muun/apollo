package io.muun.apollo.domain.utils

import io.muun.apollo.BaseTest
import io.muun.apollo.data.external.Gen
import io.muun.apollo.data.external.Globals
import io.muun.apollo.domain.libwallet.DecodedInvoice
import io.muun.apollo.domain.model.ExchangeRateWindow
import io.muun.apollo.domain.model.FeeWindow
import io.muun.apollo.domain.model.NextTransactionSize
import io.muun.apollo.domain.model.PaymentContext
import io.muun.apollo.domain.model.PaymentRequest
import io.muun.apollo.domain.model.SubmarineSwap
import io.muun.apollo.domain.model.User
import io.muun.common.bitcoinj.NetworkParametersHelper
import io.muun.common.model.DebtType
import io.muun.common.utils.BitcoinUtils
import io.muun.common.utils.LnInvoice
import org.assertj.core.api.Assertions.assertThat
import org.javamoney.moneta.Money
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.doReturn
import java.lang.Math.pow
import javax.money.Monetary

class PaymentContextTest: BaseTest() {

    private val USD = Monetary.getCurrency("USD")

    private val defaultFeeRate = 10.0
    private val minimumLightningFeeSetBySwapper = 0L // Swapper inner config, be alert to changes

    lateinit var user: User
    lateinit var exchangeRateWindow: ExchangeRateWindow
    lateinit var feeWindow: FeeWindow
    lateinit var nextTransactionSize: NextTransactionSize

    val payCtx get() =
        PaymentContext(user, exchangeRateWindow, feeWindow, nextTransactionSize)

    @Before
    fun setUp() {
        // Defaults:
        user = Gen.user(primaryCurrency = USD)
        exchangeRateWindow = Gen.exchangeRateWindow()
        feeWindow = Gen.feeWindow()
        nextTransactionSize = Gen.nextTransactionSize()

        doReturn(NetworkParametersHelper.getNetworkParametersFromName("regtest"))
            .`when`(Globals.INSTANCE).network
    }

    @Test
    fun `analyze basic fields`() {
        payCtx.analyze(Gen.payReq()).let {
            assertThat(it.totalBalance.inSatoshis).isEqualTo(totalBalance)
        }
    }

    @Test
    fun `analyze payReq with zero amount`() {
        payCtx.analyze(Gen.payReq(amount = Money.of(0, "USD"))).let {
            assertThat(it.isAmountTooSmall).isTrue()
            assertThat(it.canPayWithoutFee).isTrue()
            assertThat(it.canPayWithSelectedFee).isTrue()
            assertThat(it.canPayWithMinimumFee).isTrue()
            assertThat(it.amount.inSatoshis).isZero()
            assertThat(it.fee!!.inSatoshis).isZero()
            assertThat(it.total!!.inSatoshis).isZero()
            assertThat(it.isValid()).isFalse()
        }
    }

    @Test
    fun `analyze payReq that can never be paid`() {
        val amountInSatoshis = 10L * pow(10.0, 8.0).toLong()

        payCtx.analyze(Gen.payReq(amount = payCtx.convert(amountInSatoshis, USD))).let {
            assertThat(it.isAmountTooSmall).isFalse()
            assertThat(it.canPayWithoutFee).isFalse()
            assertThat(it.canPayWithSelectedFee).isFalse()
            assertThat(it.canPayWithMinimumFee).isFalse()
            assertThat(it.amount.inSatoshis).isEqualTo(amountInSatoshis)
            assert(it.fee == null)
            assert(it.total == null)
            assertThat(it.isValid()).isFalse()
        }
    }

    @Test
    fun `analyze payReq with valid amount plus selected fee`() {
        nextTransactionSize = Gen.nextTransactionSize(Gen.sizeForAmount(10000L to 240))

        val amount = 5000L
        val feeRate = 10.0

        val expectedFee = FeeCalculator(feeRate, nextTransactionSize).calculate(amount)
        val expectedTotal = amount + expectedFee

        val payReq = Gen.payReq(amount = payCtx.convert(amount, USD))

        payCtx.analyze(payReq).let {
            assertThat(it.isAmountTooSmall).isFalse()
            assertThat(it.canPayWithoutFee).isTrue()
            assertThat(it.canPayWithSelectedFee).isTrue()
            assertThat(it.canPayWithMinimumFee).isTrue()
            assertThat(it.amount.inSatoshis).isEqualTo(amount)
            assertThat(it.fee!!.inSatoshis).isEqualTo(expectedFee)
            assertThat(it.total!!.inSatoshis).isEqualTo(expectedTotal)
            assertThat(it.isValid()).isTrue()
        }
    }

    @Test
    fun `analyze payReq with valid amount plus minimum fee, but not selected fee`() {
        val selectedFeeRate = 10.0
        val minimumFeeRate = 1.0
        val amount = 9000L

        nextTransactionSize = Gen.nextTransactionSize(Gen.sizeForAmount(amount + 1000 to 240))
        feeWindow = Gen.feeWindow(1 to selectedFeeRate, 5 to minimumFeeRate)

        val expectedFee = FeeCalculator(selectedFeeRate, nextTransactionSize).calculate(amount)
        val expectedTotal = amount + expectedFee

        val payReq = Gen.payReq(amount = payCtx.convert(amount, USD))

        payCtx.analyze(payReq).let {
            assertThat(it.isAmountTooSmall).isFalse()
            assertThat(it.canPayWithoutFee).isTrue()
            assertThat(it.canPayWithSelectedFee).isFalse()
            assertThat(it.canPayWithMinimumFee).isTrue()
            assertThat(it.amount.inSatoshis).isEqualTo(amount)
            assertThat(it.fee!!.inSatoshis).isEqualTo(expectedFee)
            assertThat(it.total!!.inSatoshis).isEqualTo(expectedTotal)
            assertThat(it.isValid()).isFalse()
        }
    }

    @Test
    fun `analyze payReq with valid amount, but not plus any fee`() {
        val amount = 9999L

        val expectedFee = defaultFeeCalculator.calculate(amount)
        val expectedTotal = amount + expectedFee

        val payReq = Gen.payReq(amount = payCtx.convert(amount, USD))

        payCtx.analyze(payReq).let {
            assertThat(it.isAmountTooSmall).isFalse()
            assertThat(it.canPayWithoutFee).isTrue()
            assertThat(it.canPayWithSelectedFee).isFalse()
            assertThat(it.canPayWithMinimumFee).isFalse()
            assertThat(it.amount.inSatoshis).isEqualTo(amount)
            assertThat(it.fee!!.inSatoshis).isEqualTo(expectedFee)
            assertThat(it.total!!.inSatoshis).isEqualTo(expectedTotal)
            assertThat(it.isValid()).isFalse()
        }
    }

    @Test
    fun `analyze payReq with valid amount plus selected fee using TFFA`() {
        val amount = 9900L

        val expectedFee = defaultFeeCalculator.calculate(amount, takeFeeFromAmount = true)
        val expectedAmount = amount - expectedFee
        val expectedTotal = amount

        val payReq = Gen.payReq(amount = payCtx.convert(amount, USD), takeFeeFromAmount = true)

        payCtx.analyze(payReq).let {
            assertThat(it.isAmountTooSmall).isFalse()
            assertThat(it.canPayWithoutFee).isTrue()
            assertThat(it.canPayWithSelectedFee).isTrue()
            assertThat(it.canPayWithMinimumFee).isTrue()
            assertThat(it.amount.inSatoshis).isEqualTo(expectedAmount)
            assertThat(it.fee!!.inSatoshis).isEqualTo(expectedFee)
            assertThat(it.total!!.inSatoshis).isEqualTo(expectedTotal)
            assertThat(it.isValid()).isTrue()
        }
    }

    @Test
    fun `analyze payReq with valid amount plus minimum fee, but not selected fee using TFFA`() {
        val amount = 1000L

        val expectedFee = defaultFeeCalculator.calculate(amount, takeFeeFromAmount = true)
        val expectedAmount = 0L
        val expectedTotal = amount

        val payReq = Gen.payReq(amount = payCtx.convert(amount, USD), takeFeeFromAmount = true)

        payCtx.analyze(payReq).let {
            assertThat(it.isAmountTooSmall).isTrue()
            assertThat(it.canPayWithoutFee).isTrue()
            assertThat(it.canPayWithSelectedFee).isFalse()
            assertThat(it.canPayWithMinimumFee).isTrue()
            assertThat(it.amount.inSatoshis).isEqualTo(expectedAmount)
            assertThat(it.fee!!.inSatoshis).isEqualTo(expectedFee)
            assertThat(it.total!!.inSatoshis).isEqualTo(expectedTotal)
            assertThat(it.isValid()).isFalse()
        }
    }

    @Test
    fun `analyze payReq with valid amount but not with any fee using TFFA`() {
        val amount = 100L

        val expectedFee = defaultFeeCalculator.calculate(amount, takeFeeFromAmount = true)
        val expectedAmount = 0L
        val expectedTotal = amount

        val payReq = Gen.payReq(amount = payCtx.convert(amount, USD), takeFeeFromAmount = true)

        payCtx.analyze(payReq).let {
            assertThat(it.isAmountTooSmall).isTrue()
            assertThat(it.canPayWithoutFee).isTrue()
            assertThat(it.canPayWithSelectedFee).isFalse()
            assertThat(it.canPayWithMinimumFee).isFalse()
            assertThat(it.amount.inSatoshis).isEqualTo(expectedAmount)
            assertThat(it.fee!!.inSatoshis).isEqualTo(expectedFee)
            assertThat(it.total!!.inSatoshis).isEqualTo(expectedTotal)
            assertThat(it.isValid()).isFalse()
        }
    }

    @Test
    fun `analyze payReq for SumbarineSwap with valid amount`() {
        val amountInSatoshis = 100L
        val sweepFeeInSatoshis = 1000L
        val lnFeeInSatoshis = minimumLightningFeeSetBySwapper
        val outputAmountInSatohis = amountInSatoshis + sweepFeeInSatoshis + lnFeeInSatoshis

        val swap = Gen.submarineSwap(outputAmountInSatohis, sweepFeeInSatoshis, lnFeeInSatoshis)

        val payReq = submarineSwapPayReq(amountInSatoshis, swap)

        val expectedFee = feeCalculatorForSwap(swap).calculate(outputAmountInSatohis)

        val expectedAmount = amountInSatoshis
        val expectedTotal = outputAmountInSatohis + expectedFee

        payCtx.analyze(payReq).let {
            assertThat(it.isAmountTooSmall).isFalse()
            assertThat(it.canPayWithoutFee).isTrue()
            assertThat(it.canPayWithSelectedFee).isTrue()
            assertThat(it.canPayWithMinimumFee).isTrue()
            assertThat(it.amount.inSatoshis).isEqualTo(expectedAmount)
            assertThat(it.fee!!.inSatoshis).isEqualTo(expectedFee)
            assertThat(it.total!!.inSatoshis).isEqualTo(expectedTotal)
            assertThat(it.isValid()).isTrue()
        }
    }

    @Test
    fun `analyze payReq for SumbarineSwap when swap fee can't be paid`() {
        val amountInSatoshis = 500L
        val sweepFeeInSatoshis = 8500L
        val lnFeeInSatoshis = 1000L
        val outputAmountInSatohis = amountInSatoshis + sweepFeeInSatoshis + lnFeeInSatoshis

        val swap = Gen.submarineSwap(outputAmountInSatohis, sweepFeeInSatoshis, lnFeeInSatoshis)

        val payReq = submarineSwapPayReq(amountInSatoshis, swap)

        val expectedFee = feeCalculatorForSwap(swap)
                .calculate(outputAmountInSatohis, takeFeeFromAmount = true)

        val expectedTotal = outputAmountInSatohis + expectedFee

        payCtx.analyze(payReq).let {
            assertThat(it.isAmountTooSmall).isFalse()
            assertThat(it.canPayWithoutFee).isTrue()
            assertThat(it.canPayWithSelectedFee).isFalse()
            assertThat(it.canPayWithMinimumFee).isFalse()
            assertThat(it.amount.inSatoshis).isEqualTo(amountInSatoshis)
            assertThat(it.fee!!.inSatoshis).isEqualTo(expectedFee)
            assertThat(it.total!!.inSatoshis).isEqualTo(expectedTotal)
            assertThat(it.isValid()).isFalse()
        }
    }

    @Test
    fun `analyze payReq for SumbarineSwap when selected transaction fee can't be paid`() {
        val amountInSatoshis = 400L
        val sweepFeeInSatoshis = 9600L
        val lnFeeInSatoshis = minimumLightningFeeSetBySwapper
        val outputAmountInSatohis = amountInSatoshis + sweepFeeInSatoshis + lnFeeInSatoshis

        val swap = Gen.submarineSwap(outputAmountInSatohis, sweepFeeInSatoshis, lnFeeInSatoshis)

        val payReq = submarineSwapPayReq(amountInSatoshis, swap)

        val expectedFee = feeCalculatorForSwap(swap)
                .calculate(outputAmountInSatohis, takeFeeFromAmount = true)

        val expectedTotal = outputAmountInSatohis + expectedFee

        payCtx.analyze(payReq).let {
            assertThat(it.isAmountTooSmall).isFalse()
            assertThat(it.canPayWithoutFee).isTrue()
            assertThat(it.canPayWithSelectedFee).isFalse()
            assertThat(it.canPayWithMinimumFee).isFalse()
            assertThat(it.amount.inSatoshis).isEqualTo(amountInSatoshis)
            assertThat(it.fee!!.inSatoshis).isEqualTo(expectedFee)
            assertThat(it.total!!.inSatoshis).isEqualTo(expectedTotal)
            assertThat(it.isValid()).isFalse()
        }
    }

    @Test
    fun `analyze payReq for SumbarineSwap when minimum transaction fee can't be paid`() {
        val amountInSatoshis = 100L
        val sweepFeeInSatoshis = 9790L
        val lnFeeInSatoshis = 100L
        val outputAmountInSatohis = amountInSatoshis + sweepFeeInSatoshis + lnFeeInSatoshis

        val swap = Gen.submarineSwap(outputAmountInSatohis, sweepFeeInSatoshis, lnFeeInSatoshis)

        val payReq = submarineSwapPayReq(amountInSatoshis, swap)

        val expectedFee = feeCalculatorForSwap(swap)
                .calculate(outputAmountInSatohis, takeFeeFromAmount = true)

        val expectedTotal = outputAmountInSatohis + expectedFee

        payCtx.analyze(payReq).let {
            assertThat(it.isAmountTooSmall).isFalse()
            assertThat(it.canPayWithoutFee).isTrue()
            assertThat(it.canPayWithSelectedFee).isFalse()
            assertThat(it.canPayWithMinimumFee).isFalse()
            assertThat(it.amount.inSatoshis).isEqualTo(amountInSatoshis)
            assertThat(it.fee!!.inSatoshis).isEqualTo(expectedFee)
            assertThat(it.total!!.inSatoshis).isEqualTo(expectedTotal)
            assertThat(it.isValid()).isFalse()
        }
    }

    @Test
    fun `analyze payReq for SumbarineSwap when amount itself can't be paid`() {
        val amountInSatoshis = 10001L
        val sweepFeeInSatoshis = 100L
        val lnFeeInSatoshis = 100L
        val outputAmountInSatohis = amountInSatoshis + sweepFeeInSatoshis + lnFeeInSatoshis

        val swap = Gen.submarineSwap(outputAmountInSatohis, sweepFeeInSatoshis, lnFeeInSatoshis)
        val payReq = submarineSwapPayReq(amountInSatoshis, swap)

        payCtx.analyze(payReq).let {
            assertThat(it.isAmountTooSmall).isFalse()
            assertThat(it.canPayWithoutFee).isFalse()
            assertThat(it.canPayWithSelectedFee).isFalse()
            assertThat(it.canPayWithMinimumFee).isFalse()
            assertThat(it.amount.inSatoshis).isEqualTo(amountInSatoshis)
            assert(it.fee == null)
            assert(it.total == null)
            assertThat(it.isValid()).isFalse()
        }
    }

    @Test
    fun `analyze payReq for sub-dust SubmarineSwap of LEND type when amount can't be payed`() {
        nextTransactionSize = Gen.nextTransactionSize(Gen.sizeForAmount(100L to 240))

        val amountInSatoshis = 300L
        val lightningFeeInSatoshis = minimumLightningFeeSetBySwapper

        val swap = Gen.submarineSwap(
            outputAmountInSatoshis = BitcoinUtils.DUST_IN_SATOSHIS, // if client doesn't want debt
            lightningFeeInSatoshis = lightningFeeInSatoshis,
            debtType = DebtType.LEND,
            debtAmountInSatoshis = amountInSatoshis
        )

        val payReq = submarineSwapPayReq(amountInSatoshis, swap)

        payCtx.analyze(payReq).let {
            assertThat(it.hasOnChainTransaction).isFalse()
            assertThat(it.outputAmount.inSatoshis).isEqualTo(BitcoinUtils.DUST_IN_SATOSHIS)
            assertThat(it.isAmountTooSmall).isFalse()
            assertThat(it.canPayWithoutFee).isFalse()
            assertThat(it.canPayWithMinimumFee).isFalse()
            assertThat(it.canPayWithSelectedFee).isFalse()
            assertThat(it.amount.inSatoshis).isEqualTo(amountInSatoshis)
            assertThat(it.lightningFee?.inSatoshis).isEqualTo(lightningFeeInSatoshis)
            assertThat(it.fee).isNull()
            assertThat(it.total).isNull()
        }
    }

    @Test
    fun `analyze payReq for over-dust SubmarineSwap of LEND type when amount can't be payed`() {
        nextTransactionSize = Gen.nextTransactionSize(Gen.sizeForAmount(100L to 240))

        val amountInSatoshis = 900L
        val lightningFeeInSatoshis = minimumLightningFeeSetBySwapper
        val outputAmountInSatoshis = amountInSatoshis + lightningFeeInSatoshis + 400L

        val swap = Gen.submarineSwap(
            outputAmountInSatoshis = outputAmountInSatoshis,
            lightningFeeInSatoshis = lightningFeeInSatoshis,
            debtType = DebtType.LEND,
            debtAmountInSatoshis = amountInSatoshis
        )

        val payReq = submarineSwapPayReq(amountInSatoshis, swap)

        payCtx.analyze(payReq).let {
            assertThat(it.hasOnChainTransaction).isFalse()
            assertThat(it.outputAmount.inSatoshis).isEqualTo(outputAmountInSatoshis)
            assertThat(it.isAmountTooSmall).isFalse()
            assertThat(it.canPayWithoutFee).isFalse()
            assertThat(it.canPayWithMinimumFee).isFalse()
            assertThat(it.canPayWithSelectedFee).isFalse()
            assertThat(it.amount.inSatoshis).isEqualTo(amountInSatoshis)
            assertThat(it.lightningFee?.inSatoshis).isEqualTo(lightningFeeInSatoshis)
            assertThat(it.fee).isNull()
            assertThat(it.total).isNull()
        }
    }

    @Test
    fun `analyze payReq for sub-dust SubmarineSwap of LEND type when LN fee can't be payed`() {
        nextTransactionSize = Gen.nextTransactionSize(Gen.sizeForAmount(100L to 240))

        val amountInSatoshis = 130L
        val lightningFeeInSatoshis = minimumLightningFeeSetBySwapper

        val swap = Gen.submarineSwap(
            outputAmountInSatoshis = BitcoinUtils.DUST_IN_SATOSHIS, // if client doesn't want debt
            lightningFeeInSatoshis = lightningFeeInSatoshis,
            debtType = DebtType.LEND,
            debtAmountInSatoshis = amountInSatoshis
        )

        val payReq = submarineSwapPayReq(amountInSatoshis, swap)

        payCtx.analyze(payReq).let {
            assertThat(it.hasOnChainTransaction).isFalse()
            assertThat(it.outputAmount.inSatoshis).isEqualTo(BitcoinUtils.DUST_IN_SATOSHIS)
            assertThat(it.isAmountTooSmall).isFalse()
            assertThat(it.canPayWithoutFee).isFalse()
            assertThat(it.canPayWithMinimumFee).isFalse()
            assertThat(it.canPayWithSelectedFee).isFalse()
            assertThat(it.amount.inSatoshis).isEqualTo(amountInSatoshis)
            assertThat(it.lightningFee?.inSatoshis).isEqualTo(lightningFeeInSatoshis)
            assertThat(it.fee).isNull()
            assertThat(it.total).isNull()
        }
    }

    @Test
    fun `analyze payReq for over-dust SubmarineSwap of LEND type with valid amount`() {
        nextTransactionSize = Gen.nextTransactionSize(Gen.sizeForAmount(901L to 240))

        val amountInSatoshis = 900L
        val lightningFeeInSatoshis = minimumLightningFeeSetBySwapper
        val totalInSatoshis = amountInSatoshis + lightningFeeInSatoshis
        val outputAmountInSatoshis = totalInSatoshis + 400L

        val swap = Gen.submarineSwap(
            outputAmountInSatoshis = outputAmountInSatoshis,
            lightningFeeInSatoshis = lightningFeeInSatoshis,
            debtType = DebtType.LEND,
            debtAmountInSatoshis = amountInSatoshis
        )

        val payReq = submarineSwapPayReq(amountInSatoshis, swap)

        payCtx.analyze(payReq).let {
            assertThat(it.hasOnChainTransaction).isFalse()
            assertThat(it.outputAmount.inSatoshis).isEqualTo(outputAmountInSatoshis)
            assertThat(it.isAmountTooSmall).isFalse()
            assertThat(it.canPayWithoutFee).isTrue()
            assertThat(it.canPayWithMinimumFee).isTrue()
            assertThat(it.canPayWithSelectedFee).isTrue()
            assertThat(it.amount.inSatoshis).isEqualTo(amountInSatoshis)
            assertThat(it.lightningFee?.inSatoshis).isEqualTo(lightningFeeInSatoshis)
            assertThat(it.fee?.inSatoshis).isEqualTo(0)
            assertThat(it.total?.inSatoshis).isEqualTo(totalInSatoshis)
        }
    }

    @Test
    fun `analyze payReq for SubmarineSwap of COLLECT type with valid amount`() {
        val currentDebtInSat = 3000L
        nextTransactionSize = Gen.nextTransactionSize(
            Gen.sizeForAmount(10401L to 240),
            expectedDebtInSat = currentDebtInSat
        )

        // Let's assume that user has 3000 sats of DEBT ==> USER_BALANCE = 7401
        val collectableAmountInSat = currentDebtInSat

        val amountInSat = 5000L
        val lightningFeeInSat = minimumLightningFeeSetBySwapper
        val sweepFeeInSat = 0L
        val outputAmountInSat =
            amountInSat + sweepFeeInSat + lightningFeeInSat + collectableAmountInSat

        val swap = Gen.submarineSwap(
            outputAmountInSatoshis = outputAmountInSat,
            lightningFeeInSatoshis = lightningFeeInSat,
            sweepFeeInSatoshis = sweepFeeInSat,
            debtType = DebtType.COLLECT,
            debtAmountInSatoshis = collectableAmountInSat
        )

        val payReq = submarineSwapPayReq(amountInSat, swap)

        val expectedFeeInSat = feeCalculatorForSwap(swap).calculateForCollect(outputAmountInSat)
        val expectedTotalInSat = outputAmountInSat + expectedFeeInSat - collectableAmountInSat

        // Remarking that this is an alternative way to calculate total (and both should be equal)
        assertThat(expectedTotalInSat).isEqualTo(
            amountInSat + sweepFeeInSat + lightningFeeInSat + expectedFeeInSat
        )

        payCtx.analyze(payReq).let {
            assertThat(it.hasOnChainTransaction).isTrue()
            assertThat(it.outputAmount.inSatoshis).isEqualTo(outputAmountInSat)
            assertThat(it.isAmountTooSmall).isFalse()
            assertThat(it.canPayWithoutFee).isTrue()
            assertThat(it.canPayWithMinimumFee).isTrue()
            assertThat(it.canPayWithSelectedFee).isTrue()
            assertThat(it.amount.inSatoshis).isEqualTo(amountInSat)
            assertThat(it.lightningFee?.inSatoshis).isEqualTo(lightningFeeInSat)
            assertThat(it.fee?.inSatoshis).isEqualTo(expectedFeeInSat)
            assertThat(it.total?.inSatoshis).isEqualTo(expectedTotalInSat)
            assertThat(it.isValid()).isTrue()
        }
    }

    @Test
    fun `analyze payReq for SumbarineSwap of COLLECT type when swap fee can't be paid`() {
        val currentDebtInSat = 3000L
        nextTransactionSize = Gen.nextTransactionSize(
            Gen.sizeForAmount(10401L to 240),
            expectedDebtInSat = currentDebtInSat
        )

        // Let's assume that user has 3000 sats of DEBT ==> USER_BALANCE = 7401
        val collectableAmountInSat = currentDebtInSat

        val amountInSat = 7400L
        val sweepFeeInSat = 0L
        val lightningFeeInSat = minimumLightningFeeSetBySwapper
        val outputAmountInSat =
            amountInSat + sweepFeeInSat + lightningFeeInSat + collectableAmountInSat

        val swap = Gen.submarineSwap(
            outputAmountInSatoshis = outputAmountInSat,
            lightningFeeInSatoshis = lightningFeeInSat,
            sweepFeeInSatoshis = sweepFeeInSat,
            debtType = DebtType.COLLECT,
            debtAmountInSatoshis = collectableAmountInSat
        )

        val payReq = submarineSwapPayReq(amountInSat, swap)

        val expectedFeeInSat = feeCalculatorForSwap(swap).calculateForCollect(outputAmountInSat)

        val expectedTotalInSat = outputAmountInSat + expectedFeeInSat - collectableAmountInSat

        payCtx.analyze(payReq).let {
            assertThat(it.hasOnChainTransaction).isTrue()
            assertThat(it.outputAmount.inSatoshis).isEqualTo(outputAmountInSat)
            assertThat(it.isAmountTooSmall).isFalse()
            assertThat(it.canPayWithoutFee).isTrue()
            assertThat(it.canPayWithMinimumFee).isFalse()
            assertThat(it.canPayWithSelectedFee).isFalse()
            assertThat(it.amount.inSatoshis).isEqualTo(amountInSat)
            assertThat(it.lightningFee?.inSatoshis).isEqualTo(lightningFeeInSat)
            assertThat(it.fee?.inSatoshis).isEqualTo(expectedFeeInSat)
            assertThat(it.total?.inSatoshis).isEqualTo(expectedTotalInSat)
            assertThat(it.isValid()).isFalse()
        }
    }

    @Test
    fun `analyze payReq for SumbarineSwap of COLLECT type when minimum transaction fee can't be paid`() {
        val currentDebtInSat = 3000L
        nextTransactionSize = Gen.nextTransactionSize(
            Gen.sizeForAmount(10401L to 240),
            expectedDebtInSat = currentDebtInSat
        )

        // Let's assume that user has 3000 sats of DEBT ==> USER_BALANCE = 7401
        val collectableAmountInSat = currentDebtInSat

        val amountInSat = 7342L
        val sweepFeeInSat = 0L
        val lightningFeeInSat = minimumLightningFeeSetBySwapper
        val outputAmountInSat =
            amountInSat + sweepFeeInSat + lightningFeeInSat + collectableAmountInSat

        val swap = Gen.submarineSwap(
            outputAmountInSatoshis = outputAmountInSat,
            lightningFeeInSatoshis = lightningFeeInSat,
            sweepFeeInSatoshis = sweepFeeInSat,
            debtType = DebtType.COLLECT,
            debtAmountInSatoshis = collectableAmountInSat
        )

        val payReq = submarineSwapPayReq(amountInSat, swap)

        val expectedFeeInSat = feeCalculatorForSwap(swap).calculateForCollect(outputAmountInSat)

        val expectedTotalInSat = outputAmountInSat + expectedFeeInSat - collectableAmountInSat

        payCtx.analyze(payReq).let {
            assertThat(it.hasOnChainTransaction).isTrue()
            assertThat(it.outputAmount.inSatoshis).isEqualTo(outputAmountInSat)
            assertThat(it.isAmountTooSmall).isFalse()
            assertThat(it.canPayWithoutFee).isTrue()
            assertThat(it.canPayWithMinimumFee).isFalse()
            assertThat(it.canPayWithSelectedFee).isFalse()
            assertThat(it.amount.inSatoshis).isEqualTo(amountInSat)
            assertThat(it.lightningFee?.inSatoshis).isEqualTo(lightningFeeInSat)
            assertThat(it.fee?.inSatoshis).isEqualTo(expectedFeeInSat)
            assertThat(it.total?.inSatoshis).isEqualTo(expectedTotalInSat)
            assertThat(it.isValid()).isFalse()
        }
    }

    @Test
    fun `analyze payReq for SumbarineSwap of COLLECT type when amount itself can't be paid`() {
        val currentDebtInSat = 3000L
        nextTransactionSize = Gen.nextTransactionSize(
            Gen.sizeForAmount(10401L to 240),
            expectedDebtInSat = currentDebtInSat
        )

        // Let's assume that user has 3000 sats of DEBT ==> USER_BALANCE = 7401
        val collectableAmountInSat = currentDebtInSat

        val amountInSat = 7402L
        val sweepFeeInSat = 0L
        val lightningFeeInSat = minimumLightningFeeSetBySwapper
        val outputAmountInSat =
            amountInSat + sweepFeeInSat + lightningFeeInSat + collectableAmountInSat

        // Expected fee and expected total can't calculated as amoun is invalid

        val swap = Gen.submarineSwap(
            outputAmountInSatoshis = outputAmountInSat,
            lightningFeeInSatoshis = lightningFeeInSat,
            sweepFeeInSatoshis = sweepFeeInSat,
            debtType = DebtType.COLLECT,
            debtAmountInSatoshis = collectableAmountInSat
        )

        val payReq = submarineSwapPayReq(amountInSat, swap)

        payCtx.analyze(payReq).let {
            assertThat(it.hasOnChainTransaction).isTrue()
            assertThat(it.outputAmount.inSatoshis).isEqualTo(outputAmountInSat)
            assertThat(it.isAmountTooSmall).isFalse()
            assertThat(it.canPayWithoutFee).isFalse()
            assertThat(it.canPayWithMinimumFee).isFalse()
            assertThat(it.canPayWithSelectedFee).isFalse()
            assertThat(it.amount.inSatoshis).isEqualTo(amountInSat)
            assertThat(it.lightningFee?.inSatoshis).isEqualTo(lightningFeeInSat)
            assert(it.fee == null)
            assert(it.total == null)
            assertThat(it.isValid()).isFalse()
        }
    }

    @Test
    fun `analyze payReq for Swap with partial COLLECT`() {
        val currentDebtInSat = 8000L
        nextTransactionSize = Gen.nextTransactionSize(
            Gen.sizeForAmount(20401L to 240),
            expectedDebtInSat = currentDebtInSat
        )
        feeWindow = Gen.feeWindow(1 to 1.0)

        // Collect 2000 out of 8000
        val collectableAmountInSat = 2000L

        val amountInSat = 12160L
        val lightningFeeInSat = minimumLightningFeeSetBySwapper
        val sweepFeeInSat = 0L
        val outputAmountInSat =
            amountInSat + sweepFeeInSat + lightningFeeInSat + collectableAmountInSat

        val swap = Gen.submarineSwap(
            outputAmountInSatoshis = outputAmountInSat,
            lightningFeeInSatoshis = lightningFeeInSat,
            sweepFeeInSatoshis = sweepFeeInSat,
            debtType = DebtType.COLLECT,
            debtAmountInSatoshis = collectableAmountInSat
        )

        val payReq = submarineSwapPayReq(amountInSat, swap)

        val expectedFeeInSat = feeCalculatorForSwap(swap).calculateForCollect(outputAmountInSat)
        val expectedTotalInSat = outputAmountInSat + expectedFeeInSat - collectableAmountInSat

        // Remarking that this is an alternative way to calculate total (and both should be equal)
        assertThat(expectedTotalInSat).isEqualTo(
            amountInSat + sweepFeeInSat + lightningFeeInSat + expectedFeeInSat
        )

        payCtx.analyze(payReq).let {
            assertThat(it.hasOnChainTransaction).isTrue()
            assertThat(it.outputAmount.inSatoshis).isEqualTo(outputAmountInSat)
            assertThat(it.isAmountTooSmall).isFalse()
            assertThat(it.canPayWithoutFee).isTrue()
            assertThat(it.canPayWithMinimumFee).isTrue()
            assertThat(it.canPayWithSelectedFee).isTrue()
            assertThat(it.amount.inSatoshis).isEqualTo(amountInSat)
            assertThat(it.lightningFee?.inSatoshis).isEqualTo(lightningFeeInSat)
            assertThat(it.fee?.inSatoshis).isEqualTo(expectedFeeInSat)
            assertThat(it.total?.inSatoshis).isEqualTo(expectedTotalInSat)
            assertThat(it.isValid()).isTrue()
        }
    }

    @Test
    fun `analyze payReq for Swap with partial COLLECT for unpayable amount`() {
        // We setup an user with a lot of debt and try to do a collect for an amount small than
        // the total debt. The payment amount is just equal to the user balance so that the payment
        // fails cause we can't afford the on chain fee.
        val currentDebtInSat = 8000L
        nextTransactionSize = Gen.nextTransactionSize(
            Gen.sizeForAmount(20401L to 240),
            expectedDebtInSat = currentDebtInSat
        )
        feeWindow = Gen.feeWindow(1 to 1.0)

        // Collect 2000 out of 8000
        val collectableAmountInSat = 2000L

        // Try to spend it all, we shouldn't have enough for fees
        val amountInSat = nextTransactionSize.userBalance
        val lightningFeeInSat = minimumLightningFeeSetBySwapper
        val sweepFeeInSat = 0L
        val outputAmountInSat =
            amountInSat + sweepFeeInSat + lightningFeeInSat + collectableAmountInSat

        val swap = Gen.submarineSwap(
            outputAmountInSatoshis = outputAmountInSat,
            lightningFeeInSatoshis = lightningFeeInSat,
            sweepFeeInSatoshis = sweepFeeInSat,
            debtType = DebtType.COLLECT,
            debtAmountInSatoshis = collectableAmountInSat
        )

        val payReq = submarineSwapPayReq(amountInSat, swap)

        val expectedFeeInSat = feeCalculatorForSwap(swap).calculateForCollect(outputAmountInSat)
        val expectedTotalInSat = outputAmountInSat + expectedFeeInSat - collectableAmountInSat

        // Remarking that this is an alternative way to calculate total (and both should be equal)
        assertThat(expectedTotalInSat).isEqualTo(
            amountInSat + sweepFeeInSat + lightningFeeInSat + expectedFeeInSat
        )

        payCtx.analyze(payReq).let {
            assertThat(it.hasOnChainTransaction).isTrue()
            assertThat(it.outputAmount.inSatoshis).isEqualTo(outputAmountInSat)
            assertThat(it.isAmountTooSmall).isFalse()
            assertThat(it.canPayWithoutFee).isTrue()
            assertThat(it.canPayWithMinimumFee).isFalse()
            assertThat(it.canPayWithSelectedFee).isFalse()
            assertThat(it.amount.inSatoshis).isEqualTo(amountInSat)
            assertThat(it.lightningFee?.inSatoshis).isEqualTo(lightningFeeInSat)
            assertThat(it.fee?.inSatoshis).isEqualTo(expectedFeeInSat)
            assertThat(it.total?.inSatoshis).isEqualTo(expectedTotalInSat)
            assertThat(it.isValid()).isFalse()
        }
    }

    private val defaultFeeCalculator get() =
        FeeCalculator(defaultFeeRate, nextTransactionSize)

    private val totalBalance get() =
        nextTransactionSize.userBalance

    private fun submarineSwapPayReq(amountInSatoshis: Long, swap: SubmarineSwap): PaymentRequest {
        // We can't use libwallet in tests yet :(
        val invoice = LnInvoice.decode(Globals.INSTANCE.network, swap.invoice)

        return PaymentRequest(
                type = PaymentRequest.Type.TO_LN_INVOICE,
                amount = payCtx.convertToBitcoin(amountInSatoshis),
                description = "Some swap",
                invoice = DecodedInvoice(
                        swap.invoice,
                        invoice.amount?.amountInSatoshis,
                        invoice.description ?: "",
                        invoice.expirationTime,
                        invoice.destinationPubKey
                ),
                swap = swap,
                feeInSatoshisPerByte = defaultFeeCalculator.satoshisPerByte
        )
    }

    private fun feeCalculatorForSwap(swap: SubmarineSwap): FeeCalculator {
        return FeeCalculator(feeWindow.getFeeRate(swap), nextTransactionSize)
    }
}