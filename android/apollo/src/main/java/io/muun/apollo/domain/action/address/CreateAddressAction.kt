package io.muun.apollo.domain.action.address

import io.muun.apollo.data.preferences.KeysRepository
import io.muun.apollo.domain.action.base.BaseAsyncAction0
import io.muun.apollo.domain.libwallet.LibwalletBridge
import io.muun.common.Rules
import io.muun.apollo.domain.model.MuunAddressGroup
import io.muun.common.crypto.hd.Schema
import io.muun.common.utils.Preconditions
import io.muun.common.utils.RandomGenerator
import org.bitcoinj.core.NetworkParameters
import rx.Observable
import javax.inject.Inject


class CreateAddressAction @Inject constructor(
    private val keysRepository: KeysRepository,
    private val networkParameters: NetworkParameters,
    private val syncExternalAddressIndexes: SyncExternalAddressIndexesAction
): BaseAsyncAction0<MuunAddressGroup>() {

    /**
     * Sync the external address indexes with Houston.
     */
    override fun action(): Observable<MuunAddressGroup> =
        Observable.defer {
            val addresses = createMuunAddressGroup()
            syncExternalAddressIndexes.run() // we don't wait

            Observable.just(addresses)
        }

    private fun createMuunAddressGroup(): MuunAddressGroup {
        val maxUsedIndex = keysRepository.maxUsedExternalAddressIndex
        val maxWatchingIndex = keysRepository.maxWatchingExternalAddressIndex

        Preconditions.checkState(maxUsedIndex == null || maxWatchingIndex != null)
        Preconditions.checkState(maxUsedIndex == null || maxUsedIndex <= maxWatchingIndex)

        val nextIndex =
            if (maxUsedIndex == null) {
                0
            } else if (maxUsedIndex < maxWatchingIndex) {
                maxUsedIndex + 1
            } else {
                val minUsable = maxWatchingIndex - Rules.EXTERNAL_ADDRESSES_WATCH_WINDOW_SIZE
                RandomGenerator.getInt(minUsable, maxWatchingIndex + 1)
            }

        // FIXME: if the nextIndex derived key is invalid (highly improbable),
        // childPublicKey.getLastLevelIndex() will be greater than maxWatchingIndex, which is a bug:
        // it will violate the second precondition.
        val derivedPublicKeyPair = keysRepository
            .basePublicKeyPair
            .deriveFromAbsolutePath(Schema.getExternalKeyPath())
            .deriveNextValidChild(nextIndex)

        if (maxUsedIndex == null || derivedPublicKeyPair.lastLevelIndex > maxUsedIndex) {
            keysRepository.maxUsedExternalAddressIndex = derivedPublicKeyPair.lastLevelIndex
        }

        return MuunAddressGroup(
            LibwalletBridge.createAddressV3(derivedPublicKeyPair, networkParameters),
            LibwalletBridge.createAddressV4(derivedPublicKeyPair, networkParameters),
            LibwalletBridge.createAddressV5(derivedPublicKeyPair, networkParameters)
        )
    }
}