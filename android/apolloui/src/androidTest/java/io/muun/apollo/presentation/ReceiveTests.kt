package io.muun.apollo.presentation

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.muun.apollo.domain.model.AddressType
import org.javamoney.moneta.Money
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
open class ReceiveTests : BaseInstrumentationTest() {

    @Test
    fun test_01_a_user_can_receive_money_onchain_with_fixed_amount_aka_bitcoin_uri() {
        autoFlows.signUp()

        // Try with SEGWIT, default
        val amount = Money.of(0.000102, "BTC")
        autoFlows.receiveMoneyFromNetworkViaBitcoinUri(amount)

        // Try with LEGACY
        autoFlows.receiveMoneyFromNetworkViaBitcoinUri(amount, AddressType.LEGACY)

        // Try with Taproot
        autoFlows.receiveMoneyFromNetworkViaBitcoinUri(amount, AddressType.TAPROOT)

    }
}