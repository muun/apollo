package io.muun.apollo.domain.libwallet

import io.muun.apollo.BaseTest
import io.muun.apollo.domain.model.BitcoinAmount
import io.muun.common.utils.BitcoinUtils
import org.assertj.core.api.Assertions
import org.junit.Test


internal class BitcoinUriTest : BaseTest() {

    @Test
    fun testBitcoinUriToString() {

        val satoshisToBitcoins = BitcoinUtils.satoshisToBitcoins(19)
        val btcAmount = BitcoinAmount(
            19,
            BitcoinUtils.satoshisToBitcoins(19),
            BitcoinUtils.satoshisToBitcoins(19)
        )

        Assertions.assertThat(BitcoinUri.toString(btcAmount)).isEqualTo("0.00000019")
    }
}