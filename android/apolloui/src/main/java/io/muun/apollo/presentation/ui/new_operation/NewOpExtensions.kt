package io.muun.apollo.presentation.ui.new_operation

import android.os.Bundle
import io.muun.apollo.data.serialization.SerializationUtils
import io.muun.apollo.domain.model.PaymentRequest
import io.muun.apollo.domain.model.PaymentRequestJson

private const val ARG_PAY_REQ = "payment_request"

fun PaymentRequest.toBundle() = Bundle().apply {
    putString(
        ARG_PAY_REQ, SerializationUtils.serializeJson(PaymentRequestJson::class.java, toJson())
    )
}

object PaymentRequestCompanion {

    @JvmStatic
    fun fromBundle(bundle: Bundle) =
        PaymentRequest.fromJson(
            SerializationUtils.deserializeJson(
                PaymentRequestJson::class.java,
                bundle.getString(ARG_PAY_REQ)
            )
        )
}