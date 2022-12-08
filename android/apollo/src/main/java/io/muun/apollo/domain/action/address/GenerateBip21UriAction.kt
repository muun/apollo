package io.muun.apollo.domain.action.address

import io.muun.apollo.domain.action.base.BaseAsyncAction1
import io.muun.apollo.domain.action.incoming_swap.GenerateInvoiceAction
import io.muun.apollo.domain.libwallet.DecodedBitcoinUri
import io.muun.apollo.domain.libwallet.Invoice
import io.muun.apollo.domain.model.BitcoinAmount
import io.muun.apollo.domain.model.MuunAddressGroup
import org.bitcoinj.core.NetworkParameters
import rx.Observable
import javax.inject.Inject

class GenerateBip21UriAction @Inject constructor(
    private val networkParameters: NetworkParameters,
    private val generateInvoice: GenerateInvoiceAction,
    private val createAddress: CreateAddressAction,
) : BaseAsyncAction1<BitcoinAmount?, DecodedBitcoinUri>() {

    override fun action(amount: BitcoinAmount?): Observable<DecodedBitcoinUri> {
        return Observable.zip(
            createAddress.action().map(MuunAddressGroup::toAddressGroup),
            generateInvoice.action(amount?.inSatoshis)
        ) { addressGroup, invoice ->
            DecodedBitcoinUri(
                addressGroup,
                Invoice.decodeInvoice(networkParameters, invoice),
                amount
            )
        }
    }
}