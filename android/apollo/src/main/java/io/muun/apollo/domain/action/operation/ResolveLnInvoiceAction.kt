package io.muun.apollo.domain.action.operation

import androidx.annotation.VisibleForTesting
import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.data.preferences.FeeWindowRepository
import io.muun.apollo.data.preferences.KeysRepository
import io.muun.apollo.domain.action.base.BaseAsyncAction1
import io.muun.apollo.domain.errors.newop.InvalidSwapException
import io.muun.apollo.domain.errors.newop.InvoiceExpiredException
import io.muun.apollo.domain.libwallet.DecodedInvoice
import io.muun.apollo.domain.libwallet.Invoice.decodeInvoice
import io.muun.apollo.domain.model.PaymentRequest
import io.muun.apollo.domain.model.PaymentRequest.Companion.toLnInvoice
import io.muun.apollo.domain.model.SubmarineSwap
import io.muun.apollo.domain.model.SubmarineSwapRequest
import io.muun.apollo.domain.utils.DateUtils
import io.muun.common.api.SubmarineSwapJson
import io.muun.common.crypto.hd.PublicKey
import io.muun.common.crypto.hd.PublicKeyPair
import io.muun.common.utils.BitcoinUtils
import io.muun.common.utils.Encodings
import io.muun.common.utils.Hashes
import io.muun.common.utils.LnInvoice
import io.muun.common.utils.Preconditions
import libwallet.Libwallet
import org.bitcoinj.core.Address
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.core.SegwitAddress
import org.bitcoinj.core.Sha256Hash
import org.bitcoinj.script.ScriptBuilder
import org.bitcoinj.script.ScriptOpCodes.OP_CHECKSEQUENCEVERIFY
import org.bitcoinj.script.ScriptOpCodes.OP_CHECKSIG
import org.bitcoinj.script.ScriptOpCodes.OP_CHECKSIGVERIFY
import org.bitcoinj.script.ScriptOpCodes.OP_DROP
import org.bitcoinj.script.ScriptOpCodes.OP_DUP
import org.bitcoinj.script.ScriptOpCodes.OP_ELSE
import org.bitcoinj.script.ScriptOpCodes.OP_ENDIF
import org.bitcoinj.script.ScriptOpCodes.OP_EQUAL
import org.bitcoinj.script.ScriptOpCodes.OP_EQUALVERIFY
import org.bitcoinj.script.ScriptOpCodes.OP_HASH160
import org.bitcoinj.script.ScriptOpCodes.OP_IF
import org.bitcoinj.script.ScriptOpCodes.OP_SWAP
import rx.Observable
import javax.inject.Inject
import javax.inject.Singleton
import javax.money.MonetaryAmount


@Singleton
class ResolveLnInvoiceAction @Inject internal constructor(
    private val network: NetworkParameters,
    private val houstonClient: HoustonClient,
    private val keysRepository: KeysRepository,
    private val feeWindowRepository: FeeWindowRepository,
) : BaseAsyncAction1<String, PaymentRequest>() {

    companion object {
        private const val BLOCKS_IN_A_DAY = 24 * 6 // this is 144
        private const val DAYS_IN_A_WEEK = 7
    }

    override fun action(rawInvoice: String): Observable<PaymentRequest> =
        Observable.defer {
            resolveLnUri(rawInvoice)
        }

    private fun resolveLnUri(rawInvoice: String): Observable<PaymentRequest> {
        val invoice = decodeInvoice(network, rawInvoice)

        if (invoice.expirationTime.isBefore(DateUtils.now())) {
            throw InvoiceExpiredException(invoice.original)
        }

        return prepareSwap(buildSubmarineSwapRequest(invoice))
            .map { swap: SubmarineSwap -> buildPaymentRequest(invoice, swap) }
    }

    private fun buildSubmarineSwapRequest(invoice: DecodedInvoice): SubmarineSwapRequest {
        // We used to care a lot about this number for v1 swaps since it was the refund time
        // With swaps v2 we have collaborative refunds so we don't quite care and go for the max
        val swapExpirationInBlocks = BLOCKS_IN_A_DAY * DAYS_IN_A_WEEK
        return SubmarineSwapRequest(invoice.original, swapExpirationInBlocks)
    }

    private fun buildPaymentRequest(invoice: DecodedInvoice, swap: SubmarineSwap): PaymentRequest {
        val feeWindow = feeWindowRepository.fetchOne()
        val amount = getInvoiceAmount(invoice)

        if (!swap.isLend) {
            validateNonLendSwap(invoice, swap)
        }

        if (!DateUtils.isEqual(invoice.expirationTime, swap.expiresAt)) {
            throw InvalidSwapException(swap.houstonUuid)
        }

        // For AmountLess Invoices, fee rate is initially unknown
        val feeRate = if (invoice.amountInSat != null) feeWindow.getFeeRate(swap) else null
        return toLnInvoice(
            invoice,
            amount,
            invoice.description,
            swap,
            feeRate
        )
    }

    private fun validateNonLendSwap(invoice: DecodedInvoice, swap: SubmarineSwap) {
        if (invoice.amountInSat == null) {
            return  // Do not perform this validation for AmountLess Invoices
        }

        Preconditions.checkNotNull(swap.fundingOutput.outputAmountInSatoshis)
        Preconditions.checkNotNull(swap.fundingOutput.debtAmountInSatoshis)
        Preconditions.checkNotNull(swap.fundingOutput.confirmationsNeeded)
        Preconditions.checkNotNull(swap.fees)

        val actualOutputAmount = swap.fundingOutput.outputAmountInSatoshis!!
        var expectedOutputAmount = invoice.amountInSat + swap.totalFeesInSat()!!

        if (swap.isCollect) {
            expectedOutputAmount += swap.fundingOutput.debtAmountInSatoshis!!
        }

        if (actualOutputAmount != expectedOutputAmount) {
            throw InvalidSwapException(swap.houstonUuid)
        }
    }

    private fun getInvoiceAmount(invoice: DecodedInvoice): MonetaryAmount? {
        return if (invoice.amountInSat != null) {
            BitcoinUtils.satoshisToBitcoins(invoice.amountInSat)
        } else
            null
    }

    /**
     * Create a new Submarine Swap.
     */
    @VisibleForTesting
    fun prepareSwap(request: SubmarineSwapRequest): Observable<SubmarineSwap> {
        val basePublicKeyPair = keysRepository.basePublicKeyPair
        return houstonClient.createSubmarineSwap(request)
            .doOnNext { submarineSwap: SubmarineSwap ->
                val isValid = validateSwap(
                    request.invoice,
                    request.swapExpirationInBlocks,
                    basePublicKeyPair,
                    submarineSwap.toJson(),  // Needs to be a common's class
                    network
                )
                if (!isValid) {
                    throw InvalidSwapException(submarineSwap.houstonUuid)
                }
            }
    }

    // TODO everything down this line should be removed and libwallet code be used instead
    // TODO everything down this line should be removed and libwallet code be used instead
    /**
     * Validate Submarine Swap Server response. The end goal is to verify that the redeem script
     * returned by the server is the script that is actually encoded in the reported swap address.
     */
    fun validateSwap(
        originalInvoice: String,
        originalExpirationInBlocks: Int,
        userPublicKeyPair: PublicKeyPair,
        swapJson: SubmarineSwapJson,
        network: NetworkParameters?,
    ): Boolean {
        val fundingOutput = swapJson.fundingOutput

        // Check to avoid handling older swaps (e.g Swaps V1). With every swap version upgrade,
        // there will always be a window of time where newer clients can receive a previously
        // created swap with an older version (scanning same ln invoice of an already created swap).
        // We decided to save a lot of trouble and code and not support this edge case. This check
        // (and this comment) makes this decision EXPLICIT :)
        Preconditions.checkArgument(fundingOutput.scriptVersion.toLong() == Libwallet.AddressVersionSwapsV2)

        // Check that the embedded invoice is the same as the original
        if (!originalInvoice.equals(swapJson.invoice, ignoreCase = true)) {
            return false
        }

        // Parse invoice
        val invoice = LnInvoice.decode(network, originalInvoice)

        // Check that the receiver is the same as the original
        if (invoice.destinationPubKey != swapJson.receiver.publicKey) {
            return false
        }

        // Check that the payment hash matches the invoice
        if (invoice.id != fundingOutput.serverPaymentHashInHex) {
            return false
        }
        if (originalExpirationInBlocks != fundingOutput.expirationInBlocks) {
            return false
        }
        val userPublicKey: PublicKey = PublicKey.fromJson(fundingOutput.userPublicKey)!!
        val muunPublicKey: PublicKey = PublicKey.fromJson(fundingOutput.muunPublicKey)!!
        val derivedPublicKeyPair = userPublicKeyPair
            .deriveFromAbsolutePath(fundingOutput.userPublicKey!!.path)

        // Check that the user public key belongs to the user
        if (derivedPublicKeyPair.userPublicKey != userPublicKey) {
            return false
        }

        // Check that the muun public key belongs to muun
        if (derivedPublicKeyPair.muunPublicKey != muunPublicKey) {
            return false
        }
        val paymentHashInHex = fundingOutput.serverPaymentHashInHex

        // Check that the witness script was computed according to the given parameters
        val witnessScript = createWitnessScript(
            Encodings.hexToBytes(paymentHashInHex),
            userPublicKey.getPublicKeyBytes(),
            muunPublicKey.getPublicKeyBytes(),
            Encodings.hexToBytes(fundingOutput.serverPublicKeyInHex),
            fundingOutput.expirationInBlocks!!.toLong()
        )

        // Check that the script hashes to the output address we'll be using
        val outputAddress: Address = createAddress(network, witnessScript)
        if (!outputAddress.toString().equals(fundingOutput.outputAddress)) {
            return false
        }

        // Check other values for internal consistency
        val preimageInHex = swapJson.preimageInHex
        if (preimageInHex != null) {
            val calculatedHash = Hashes.sha256(Encodings.hexToBytes(preimageInHex))
            if (paymentHashInHex != Encodings.bytesToHex(calculatedHash)) {
                return false
            }
        }
        return true
    }


    /**
     * Create the witness script for spending the submarine swap output.
     */
    fun createWitnessScript(
        swapPaymentHash256: ByteArray?,
        userPublicKey: ByteArray?,
        muunPublicKey: ByteArray?,
        swapServerPublicKey: ByteArray?,
        numBlocksForExpiration: Long,
    ): ByteArray {

        // per bip 68 (the one where relative lock-time is defined)
        val maxRelativeLockTimeBlocks = 0xFFFF
        Preconditions.checkArgument(numBlocksForExpiration <= maxRelativeLockTimeBlocks)

        // It turns out that the payment hash present in an invoice is just the SHA256 of the
        // payment preimage, so we still have to do a pass of RIPEMD160 before pushing it to the
        // script
        val swapPaymentHash160 = Hashes.ripemd160(swapPaymentHash256)
        val serverPublicKeyHash160 = Hashes.sha256Ripemd160(muunPublicKey)

        // Equivalent miniscript (http://bitcoin.sipa.be/miniscript/):
        // or(
        //   and(pk(userPublicKey), pk(swapServerPublicKey)),
        //   or(
        //     and(pk(swapServerPublicKey), hash160(swapPaymentHash160)),
        //     and(pk(userPublicKey), and(pk(muunPublicKey), older(numBlocksForExpiration)))
        //   )
        // )
        //
        // However, we differ in that the size of the script was heavily optimized for spending the
        // first two branches (the collaborative close and the unilateral close by swapper), which
        // are the most probable to be used.
        return ScriptBuilder() // Push the user public key to the second position of the stack
            .data(userPublicKey)
            .op(OP_SWAP) // Check whether the first stack item was a valid swap server signature
            .data(swapServerPublicKey)
            .op(OP_CHECKSIG) // If the swap server signature was correct
            .op(OP_IF)
            .op(OP_SWAP) // Check whether the second stack item was the payment preimage
            .op(OP_DUP)
            .op(OP_HASH160)
            .data(swapPaymentHash160)
            .op(OP_EQUAL) // If the preimage was correct
            .op(OP_IF) // We are done, leave just one true-ish item in the stack (there're 2
            // remaining items)
            .op(OP_DROP) // If the second stack item wasn't a valid payment preimage
            .op(OP_ELSE) // Validate that the second stack item was a valid user signature
            .op(OP_SWAP)
            .op(OP_CHECKSIG)
            .op(OP_ENDIF) // If the first stack item wasn't a valid server signature
            .op(OP_ELSE) // Validate that the blockchain height is big enough
            .number(numBlocksForExpiration)
            .op(OP_CHECKSEQUENCEVERIFY)
            .op(OP_DROP) // Validate that the second stack item was a valid user signature
            .op(OP_CHECKSIGVERIFY) // Validate that the third stack item was the muun public key
            .op(OP_DUP)
            .op(OP_HASH160)
            .data(serverPublicKeyHash160)
            .op(OP_EQUALVERIFY)
            // Notice that instead of directly pushing the public key here and checking the
            // signature P2PK-style, we pushed the hash of the public key, and require an
            // extra stack item with the actual public key, verifying the signature and
            // public key P2PKH-style.
            //
            // This trick reduces the on-chain footprint of the muun key from 33 bytes to
            // 20 bytes for the collaborative, and swap server's non-collaborative branches,
            // which are the most frequent ones.
            // Validate that the fourth stack item was a valid server signature
            .op(OP_CHECKSIG)
            .op(OP_ENDIF)
            .build()
            .getProgram()
    }

    /**
     * Create an address.
     */
    fun createAddress(network: NetworkParameters?, witnessScript: ByteArray?): Address {
        val witnessScriptHash: ByteArray = Sha256Hash.hash(witnessScript)
        return SegwitAddress.fromHash(network, witnessScriptHash)
    }
}