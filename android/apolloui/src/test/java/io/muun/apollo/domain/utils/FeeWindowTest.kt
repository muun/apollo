package io.muun.apollo.domain.utils

import io.muun.apollo.BaseTest
import io.muun.apollo.domain.model.FeeWindow
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.Test
import org.threeten.bp.ZonedDateTime

class FeeWindowTest: BaseTest() {

    val singleFeeWindow = createFeeWindow(1 to 5.6)
    val someFeeWindow = createFeeWindow(2 to 2.3, 5 to 7.2, 20 to 18.7)

    private val margin = 0.000001

    @Test
    fun `returns the fastest fee rate`() {
        assertThat(someFeeWindow.fastestFeeInSatoshisPerByte).isEqualTo(2.3)
    }

    @Test
    fun `returns the only fee rate as fastest`() {
        assertThat(singleFeeWindow.fastestFeeInSatoshisPerByte).isEqualTo(5.6)
    }

    @Test
    fun `returns the exact target as closest, if present`() {
        val targetedFees = someFeeWindow.targetedFees

        for ((target, expectedRate) in targetedFees.entries) {
            val rate = someFeeWindow.getMinimumFeeInSatoshisPerByte(target)

            assertThat(rate).isCloseTo(expectedRate, within(margin))
        }
    }

    @Test
    fun `returns the closest lower target`() {
        assertThat(someFeeWindow.getMinimumFeeInSatoshisPerByte(4))
            .isCloseTo(2.3, within(margin))

        assertThat(someFeeWindow.getMinimumFeeInSatoshisPerByte(15))
            .isCloseTo(7.2, within(margin))

        assertThat(someFeeWindow.getMinimumFeeInSatoshisPerByte(22))
            .isCloseTo(18.7, within(margin))
    }

    @Test
    fun `returns the lowest target by default`() {
        assertThat(someFeeWindow.getMinimumFeeInSatoshisPerByte(1))
            .isCloseTo(2.3, within(margin))
    }

    @Test
    fun `returns the only fee rate as closest`() {
        for (i in listOf(1, 6, 18, 24)) {
            assertThat(singleFeeWindow.getMinimumFeeInSatoshisPerByte(i))
                .isCloseTo(5.6, within(margin))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `fails check when confirmation target is 0`() {
        someFeeWindow.getMinimumFeeInSatoshisPerByte(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `fails check when confirmation target is negative`() {
        someFeeWindow.getMinimumFeeInSatoshisPerByte(-1)
    }

    private fun createFeeWindow(vararg targetToRate: Pair<Int, Double>) =
        FeeWindow(1, ZonedDateTime.now(), mapOf(*targetToRate), 1, 1, 1)
}