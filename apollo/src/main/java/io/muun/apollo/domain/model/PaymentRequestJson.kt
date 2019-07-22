package io.muun.apollo.domain.model

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility
import io.muun.apollo.domain.model.PaymentRequest.Type
import io.muun.common.api.Contact
import io.muun.common.api.SubmarineSwapJson
import io.muun.common.utils.LnInvoice
import javax.money.MonetaryAmount

@JsonAutoDetect(fieldVisibility = Visibility.ANY)
data class PaymentRequestJson(var type: Type? = null,
                              var amount: MonetaryAmount? = null,
                              var description: String? = null,
                              var contact: Contact? = null,
                              var address: String? = null,
                              var hardwareWallet: HardwareWallet? = null,
                              var invoice: String? = null,
                              var swap: SubmarineSwapJson? = null,
                              var feeInSatoshisPerByte: Double? = null,
                              var takeFeeFromAmount: Boolean = false)