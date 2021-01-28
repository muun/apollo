package io.muun.apollo.domain

import io.muun.apollo.BaseTest
import io.muun.apollo.data.external.Gen
import io.muun.apollo.domain.errors.InsufficientFundsError
import io.muun.apollo.domain.model.NextTransactionSize
import io.muun.apollo.domain.utils.FeeCalculator
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class FeeCalculatorTest : BaseTest() {
    companion object {
        const val FEE_RATE: Double = 10.0

        val emptyNts = Gen.nextTransactionSize()

        val defaultNts = Gen.nextTransactionSize(
            Gen.sizeForAmount(103456L to 110),
            Gen.sizeForAmount(20345678L to 230),
            Gen.sizeForAmount(303456789L to 340),
            Gen.sizeForAmount(703456789L to 580)
        )

        val singleNts = Gen.nextTransactionSize(
            Gen.sizeForAmount(12345L to 400)
        )

        val negativeUtxoNts = Gen.nextTransactionSize(
            Gen.sizeForAmount(48216L to 840),
            Gen.sizeForAmount(48880L to 1366)
        )

        val singleNegativeUtxoNts = Gen.nextTransactionSize(Gen.sizeForAmount(664L to 840))

        val emptyFeeCalculator = FeeCalculator(FEE_RATE, NextTransactionSize(listOf(), 1, 0))
        val singleEntryFeeCalculator = FeeCalculator(FEE_RATE, singleNts)
        val defaultFeeCalculator = FeeCalculator(FEE_RATE, defaultNts)
        val negativeUtxoFeeCalculator = FeeCalculator(FEE_RATE, negativeUtxoNts)
        val singleNegativeUtxoFeeCalculator = FeeCalculator(FEE_RATE, singleNegativeUtxoNts)

        val allFeeCalculators = listOf(
            emptyFeeCalculator,
            singleEntryFeeCalculator,
            defaultFeeCalculator,
            negativeUtxoFeeCalculator,
            singleNegativeUtxoFeeCalculator
        )
    }

    @Test
    fun `calculates when amount is zero`() {
        allFeeCalculators.forEach {
            assertThat(it.calculate(0)).isZero()
        }
    }

    @Test
    fun `calculates when amount is zero with TFFA`() {
        allFeeCalculators.forEach {
            assertThat(it.calculate(0, takeFeeFromAmount = true)).isZero()
        }
    }

    @Test(expected = InsufficientFundsError::class)
    fun `fails when balance is zero`() {
        emptyFeeCalculator.calculate(1)
    }

    @Test(expected = InsufficientFundsError::class)
    fun `fails when balance is zero with TFFA`() {
        emptyFeeCalculator.calculate(1, takeFeeFromAmount = true)
    }

    @Test(expected = InsufficientFundsError::class)
    fun `fails when amount exceeds balance`() {
        defaultFeeCalculator.calculate(defaultNts.userBalance + 1)
    }

    @Test(expected = InsufficientFundsError::class)
    fun `calculates when amount exceeds balance with TFFA`() {
        defaultFeeCalculator.calculate(defaultNts.userBalance + 1, takeFeeFromAmount = true)
    }

    @Test
    fun `calculates when amount plus fee exceeds balance`() {
        val amountInSatoshis = defaultNts.userBalance - 1
        val feeInSatoshis = (defaultNts.sizeProgression.last().sizeInBytes * FEE_RATE).toLong()

        defaultFeeCalculator.calculate(amountInSatoshis).let {
            assertThat(it).isEqualTo(feeInSatoshis)
        }
    }

    @Test
    fun `calculates reduced amount and fee with TFFA`() {
        check(defaultNts.sizeProgression.size > 2) // we don't want to use all funds by accident

        val feeInSatoshis = (defaultNts.sizeProgression[1].sizeInBytes * FEE_RATE).toLong()
        val amountInSatoshis = defaultNts.sizeProgression[1].amountInSatoshis / 2

        defaultFeeCalculator.calculate(amountInSatoshis, takeFeeFromAmount = true).let {
            assertThat(it).isEqualTo(feeInSatoshis)
        }
    }

    @Test
    fun `calculates when no amount is left after TFFA`() {
        val feeInSatoshis = (defaultNts.sizeProgression[0].sizeInBytes * FEE_RATE).toLong()
        val amountInSatoshis = 10L

        defaultFeeCalculator.calculate(amountInSatoshis, takeFeeFromAmount = true).let {
            assertThat(it).isEqualTo(feeInSatoshis)
        }
    }

    @Test
    fun `calculates use-all-funds fee with TFFA`() {
        val feeInSatoshis = (defaultNts.sizeProgression.last().sizeInBytes * FEE_RATE).toLong()
        val amountInSatoshis = defaultNts.userBalance

        defaultFeeCalculator.calculate(amountInSatoshis, takeFeeFromAmount = true).let {
            assertThat(it).isEqualTo(feeInSatoshis)
        }
    }

    @Test
    fun `calculates when paying fee does not require an additional UTXO`() {
        for (sizeForAmount in defaultNts.sizeProgression) {
            val amountInSatoshis = sizeForAmount.amountInSatoshis / 2
            val feeInSatoshis = (sizeForAmount.sizeInBytes * FEE_RATE).toLong()

            defaultFeeCalculator.calculate(amountInSatoshis).let {
                assertThat(it).isEqualTo(feeInSatoshis)
            }
        }
    }

    @Test
    fun `calculates when paying fee requires an additional UTXO`() {
        for (i in 0 until defaultNts.sizeProgression.size - 1) {
            val sizeForAmount = defaultNts.sizeProgression[i]
            val nextSizeForAmount = defaultNts.sizeProgression[i + 1]

            val amountInSatoshis = sizeForAmount.amountInSatoshis - 1
            val feeInSatoshis = (nextSizeForAmount.sizeInBytes * FEE_RATE).toLong()

            defaultFeeCalculator.calculate(amountInSatoshis).let {
                assertThat(it).isEqualTo(feeInSatoshis)
            }
        }
    }

    @Test
    fun `calculates when negative UTXOs are larger than positive UTXOs`() {
        val totalBalance = singleNegativeUtxoNts.sizeProgression[0].amountInSatoshis

        val amountInSatoshis = 1L
        val expectedFee = 8400L

        singleNegativeUtxoFeeCalculator.calculate(amountInSatoshis).let {
            assertThat(it).isEqualTo(expectedFee)
            assertThat(it).isGreaterThan(totalBalance)
        }
    }
}
