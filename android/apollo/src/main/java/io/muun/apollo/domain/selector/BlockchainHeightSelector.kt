package io.muun.apollo.domain.selector

import io.muun.apollo.data.external.Globals
import io.muun.apollo.data.preferences.BlockchainHeightRepository
import io.muun.apollo.domain.libwallet.toLibwalletModel
import io.muun.common.bitcoinj.BlockHelpers
import libwallet.Libwallet
import rx.Observable
import javax.inject.Inject
import kotlin.math.max

class BlockchainHeightSelector @Inject constructor(
    private val blockchainHeightRepository: BlockchainHeightRepository
) {

    companion object {
        var DEBUG_BLOCKS_TO_TAPROOT: Int? = null

        fun getBlocksInHours(blocks: Int) =
            if (blocks == 0) {
                0 // separate case, because estimations are rounded up to 1
            } else {
                max(BlockHelpers.timeInSecsForBlocksWithCertainty(blocks, 0.75) / 3600, 1)
            }
    }

    fun watch(): Observable<Int> =
        blockchainHeightRepository.fetch()

    fun watchBlocksToTaproot(): Observable<Int> =
        watch().map { height -> calcBlocksToTaproot(height) }

    fun get(): Int =
        watch().toBlocking().first()

    private fun calcBlocksToTaproot(blockchainHeight: Int): Int {
        if (DEBUG_BLOCKS_TO_TAPROOT != null) {
            return DEBUG_BLOCKS_TO_TAPROOT!!
        }

        val taprootHeight = Libwallet.getUserActivatedFeatureTaproot()
            .blockheight(Globals.INSTANCE.network.toLibwalletModel())

        return max(taprootHeight - blockchainHeight, 0).toInt()
    }
}