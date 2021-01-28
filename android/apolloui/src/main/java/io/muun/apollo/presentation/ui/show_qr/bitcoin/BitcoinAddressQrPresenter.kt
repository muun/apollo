package io.muun.apollo.presentation.ui.show_qr.bitcoin

import android.os.Bundle
import icepick.State
import io.muun.apollo.domain.action.address.CreateAddressAction
import io.muun.apollo.presentation.analytics.AnalyticsEvent
import io.muun.apollo.presentation.ui.show_qr.QrPresenter
import io.muun.common.crypto.MuunAddressGroup
import javax.inject.Inject

open class BitcoinAddressQrPresenter @Inject constructor(
    private val createAddress: CreateAddressAction
) : QrPresenter<BitcoinAddressView>() {

    @State
    lateinit var legacyAddress: String

    @State
    lateinit var segwitAddress: String

    @State
    @JvmField
    var addressFormat: AddressFormat = AddressFormat.SEGWIT

    override fun setUp(arguments: Bundle?) {
        super.setUp(arguments)

        // TODO: this should NOT be invoked using the `action()` method, but we can't invoke this
        // action from the main thread otherwise. What we should do is refactor our child presenters
        // to observe this result.
        try {
            onAddressesReady(createAddress.actionNow())
        } catch (error: Throwable) {
            handleError(error)
        }
    }

    override fun getQrContent() =
        when (addressFormat) {
            AddressFormat.SEGWIT -> segwitAddress
            AddressFormat.LEGACY -> legacyAddress
        }

    override fun getEntryEvent(): AnalyticsEvent {
        return AnalyticsEvent.S_RECEIVE(
            getTrackingParam(),
            parentPresenter.getOrigin()
        )
    }

    override fun hasLoadedCorrectly(): Boolean =
        ::legacyAddress.isInitialized

    override fun showFullContent() {
        view.showFullContent(getQrContent(), addressFormat)
    }

    private fun onAddressesReady(newAddresses: MuunAddressGroup) {
        if (!::legacyAddress.isInitialized) {
            legacyAddress = newAddresses.legacy.address
        }

        if (!::segwitAddress.isInitialized) {
            segwitAddress = newAddresses.segwit.address
        }

        updateView()
    }

    fun switchAddressFormat() {
        addressFormat = when (addressFormat) {
            AddressFormat.SEGWIT -> AddressFormat.LEGACY
            AddressFormat.LEGACY -> AddressFormat.SEGWIT
        }

        updateView()
    }

    private fun updateView() {
        val address = if (addressFormat == AddressFormat.SEGWIT) segwitAddress else legacyAddress
        view.setAddress(address, addressFormat)
    }

    fun showHelp() {
        navigator.navigateToBitoinAddressHelp(context)
    }

    private fun getTrackingParam() =
        if (addressFormat == AddressFormat.SEGWIT) {
            AnalyticsEvent.S_RECEIVE_TYPE.SEGWIT_ADDRESS
        } else {
            AnalyticsEvent.S_RECEIVE_TYPE.LEGACY_ADDRESS
        }
}
