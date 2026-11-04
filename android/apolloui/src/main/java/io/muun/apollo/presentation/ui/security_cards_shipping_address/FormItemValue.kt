package io.muun.apollo.presentation.ui.security_cards_shipping_address

import android.os.Parcelable
import androidx.annotation.StringRes
import io.muun.apollo.R
import io.muun.common.bitcoinj.ValidationHelpers
import kotlinx.parcelize.Parcelize

/** A form field's content plus the validation error it's showing, if any. */
@Parcelize
data class FormItemValue(
    val value: String = "",
    @StringRes val error: Int? = null,
) : Parcelable

/** Applies one validation rule, keeping the first error a previous rule already found. */
private fun FormItemValue.rule(
    @StringRes errorRes: Int,
    isValid: (String) -> Boolean,
): FormItemValue {
    if (error != null) {
        return this
    }

    return copy(error = if (isValid(value)) null else errorRes)
}

fun FormItemValue.notBlank(): FormItemValue {
    return rule(R.string.security_cards_shipping_address_error_empty, String::isNotBlank)
}

fun FormItemValue.validEmail(): FormItemValue {
    return rule(
        R.string.security_cards_shipping_address_error_invalid_email,
        ValidationHelpers::isValidEmail,
    )
}
