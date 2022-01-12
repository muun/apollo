package io.muun.apollo.presentation.ui.new_operation

import io.muun.apollo.domain.model.BitcoinAmount
import io.muun.apollo.domain.model.BitcoinUnit

class DisplayAmount(
    val amount: BitcoinAmount,
    val bitcoinUnit: BitcoinUnit,
    val isValid: Boolean = true
) {

    constructor(
        amt: newop.BitcoinAmount,
        btcUnit: BitcoinUnit,
        isValid: Boolean = true
    ) : this(
        BitcoinAmount.fromLibwallet(amt), btcUnit, isValid
    )
}