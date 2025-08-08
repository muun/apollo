package io.muun.apollo.domain.errors

import io.muun.common.exception.PotentialBug

class MoneyDecorationError(
    override val message: String,
    newCaretPosition: Int,
    resultLength: Int,
    resultString: String,
    inputStringLength: Int,
    inputString: String,
    decimalSeparator: Char,
    groupingSeparator: Char,
    maxFractionalDigits: Int,
    integerPartSize: Int,
    selectionStart: Int,
    start: Int,
    after: Int,
) : MuunError(message), PotentialBug {

    init {
        metadata["newCaretPosition"] = newCaretPosition
        metadata["resultLength"] = resultLength
        metadata["resultString"] = resultString
        metadata["inputStringLength"] = inputStringLength
        metadata["inputString"] = inputString
        metadata["decimalSeparator"] = decimalSeparator
        metadata["groupingSeparator"] = groupingSeparator
        metadata["maxFractionalDigits"] = maxFractionalDigits
        metadata["integerPartSize"] = integerPartSize
        metadata["selectionStart"] = selectionStart
        metadata["start"] = start
        metadata["after"] = after

    }

}