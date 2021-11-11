package io.muun.apollo.domain.action.operation;

import io.muun.apollo.data.net.HoustonClient;
import io.muun.apollo.data.preferences.FeeWindowRepository;
import io.muun.apollo.data.preferences.KeysRepository;
import io.muun.apollo.domain.action.base.BaseAsyncAction1;
import io.muun.apollo.domain.errors.InvalidSwapException;
import io.muun.apollo.domain.errors.InvoiceExpiredException;
import io.muun.apollo.domain.libwallet.DecodedInvoice;
import io.muun.apollo.domain.libwallet.Invoice;
import io.muun.apollo.domain.model.FeeWindow;
import io.muun.apollo.domain.model.OperationUri;
import io.muun.apollo.domain.model.PaymentRequest;
import io.muun.apollo.domain.model.SubmarineSwap;
import io.muun.apollo.domain.model.SubmarineSwapRequest;
import io.muun.apollo.domain.utils.DateUtils;
import io.muun.common.api.SubmarineSwapFundingOutputJson;
import io.muun.common.api.SubmarineSwapJson;
import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.crypto.hd.PublicKeyPair;
import io.muun.common.utils.BitcoinUtils;
import io.muun.common.utils.Encodings;
import io.muun.common.utils.Hashes;
import io.muun.common.utils.LnInvoice;
import io.muun.common.utils.Preconditions;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import libwallet.Libwallet;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.script.ScriptBuilder;
import rx.Observable;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.money.MonetaryAmount;

import static org.bitcoinj.script.ScriptOpCodes.OP_CHECKSEQUENCEVERIFY;
import static org.bitcoinj.script.ScriptOpCodes.OP_CHECKSIG;
import static org.bitcoinj.script.ScriptOpCodes.OP_CHECKSIGVERIFY;
import static org.bitcoinj.script.ScriptOpCodes.OP_DROP;
import static org.bitcoinj.script.ScriptOpCodes.OP_DUP;
import static org.bitcoinj.script.ScriptOpCodes.OP_ELSE;
import static org.bitcoinj.script.ScriptOpCodes.OP_ENDIF;
import static org.bitcoinj.script.ScriptOpCodes.OP_EQUAL;
import static org.bitcoinj.script.ScriptOpCodes.OP_EQUALVERIFY;
import static org.bitcoinj.script.ScriptOpCodes.OP_HASH160;
import static org.bitcoinj.script.ScriptOpCodes.OP_IF;
import static org.bitcoinj.script.ScriptOpCodes.OP_SWAP;

@Singleton
public class ResolveLnUriAction extends BaseAsyncAction1<OperationUri, PaymentRequest> {

    private final NetworkParameters network;
    private final HoustonClient houstonClient;
    private final KeysRepository keysRepository;
    private final FeeWindowRepository feeWindowRepository;

    private static final int BLOCKS_IN_A_DAY = 24 * 6; // this is 144
    private static final int DAYS_IN_A_WEEK = 7;
    private static final int DYNAMIC_TARGET_THRESHOLD_IN_SATS = 150_000;

    /**
     * Resolves a LightningNetwork OperationUri.
     */
    @Inject
    ResolveLnUriAction(NetworkParameters network,
                       HoustonClient houstonClient,
                       KeysRepository keysRepository,
                       FeeWindowRepository feeWindowRepository) {
        this.network = network;
        this.houstonClient = houstonClient;
        this.keysRepository = keysRepository;
        this.feeWindowRepository = feeWindowRepository;
    }

    @Override
    public Observable<PaymentRequest> action(OperationUri operationUri) {
        return resolveLnUri(operationUri);
    }

    private Observable<PaymentRequest> resolveLnUri(OperationUri uri) {
        final DecodedInvoice invoice = Invoice.INSTANCE
                .decodeInvoice(network, uri.getLnInvoice().get());

        if (invoice.getExpirationTime().isBefore(DateUtils.now())) {
            throw new InvoiceExpiredException(invoice.getOriginal());
        }

        return prepareSwap(buildSubmarineSwapRequest(invoice))
                .map(swap -> buildPaymentRequest(invoice, swap));
    }

    private SubmarineSwapRequest buildSubmarineSwapRequest(DecodedInvoice invoice) {
        // We used to care a lot about this number for v1 swaps since it was the refund time
        // With swaps v2 we have collaborative refunds so we don't quite care and go for the max
        return new SubmarineSwapRequest(
                invoice.getOriginal(),
                BLOCKS_IN_A_DAY * DAYS_IN_A_WEEK
        );
    }

    @NonNull
    private PaymentRequest buildPaymentRequest(DecodedInvoice invoice, SubmarineSwap swap) {
        final FeeWindow feeWindow = feeWindowRepository.fetchOne();
        final MonetaryAmount amount = getInvoiceAmount(invoice);

        if (! swap.isLend()) {
            validateNonLendSwap(invoice, swap);
        }

        if (!DateUtils.isEqual(invoice.getExpirationTime(), swap.getExpiresAt())) {
            throw new InvalidSwapException(swap.houstonUuid);
        }

        // For AmountLess Invoices, fee rate is initially unknown
        final Double feeRate = invoice.getAmountInSat() != null ? feeWindow.getFeeRate(swap) : null;

        return PaymentRequest.toLnInvoice(
                invoice,
                amount,
                invoice.getDescription(),
                swap,
                feeRate
        );
    }

    private void validateNonLendSwap(DecodedInvoice invoice, SubmarineSwap swap) {

        if (invoice.getAmountInSat() == null) {
            return; // Do not perform this validation for AmountLess Invoices
        }

        Preconditions.checkNotNull(swap.getFundingOutput().getOutputAmountInSatoshis());
        Preconditions.checkNotNull(swap.getFundingOutput().getDebtAmountInSatoshis());
        Preconditions.checkNotNull(swap.getFundingOutput().getConfirmationsNeeded());
        Preconditions.checkNotNull(swap.getFees());

        final long actualOutputAmount = swap.getFundingOutput().getOutputAmountInSatoshis();

        long expectedOutputAmount = invoice.getAmountInSat()
                + swap.getFees().getSweepInSats()
                + swap.getFees().getLightningInSats();

        if (swap.isCollect()) {
            expectedOutputAmount += swap.getFundingOutput().getDebtAmountInSatoshis();
        }

        if (actualOutputAmount != expectedOutputAmount) {
            throw new InvalidSwapException(swap.houstonUuid);
        }
    }

    private MonetaryAmount getInvoiceAmount(DecodedInvoice invoice) {

        if (invoice.getAmountInSat() != null) {
            return BitcoinUtils.satoshisToBitcoins(invoice.getAmountInSat());
        }

        return null;
    }

    /**
     * Create a new Submarine Swap.
     */
    @VisibleForTesting
    public Observable<SubmarineSwap> prepareSwap(SubmarineSwapRequest request) {
        final PublicKeyPair basePublicKeyPair = keysRepository.getBasePublicKeyPair();
        return houstonClient.createSubmarineSwap(request)
                .doOnNext(submarineSwap -> {
                    final boolean isValid = validateSwap(
                            request.invoice,
                            request.swapExpirationInBlocks,
                            basePublicKeyPair,
                            submarineSwap.toJson(), // Needs to be a common's class
                            network
                    );

                    if (!isValid) {
                        throw new InvalidSwapException(submarineSwap.houstonUuid);
                    }
                });
    }

    // TODO everything down this line should be removed and libwallet code be used instead
    /**
     * Validate Submarine Swap Server response. The end goal is to verify that the redeem script
     * returned by the server is the script that is actually encoded in the reported swap address.
     */
    public static boolean validateSwap(String originalInvoice,
                                       Integer originalExpirationInBlocks,
                                       PublicKeyPair userPublicKeyPair,
                                       SubmarineSwapJson swapJson,
                                       NetworkParameters network) {

        final SubmarineSwapFundingOutputJson fundingOutput = swapJson.fundingOutput;

        // Check to avoid handling older swaps (e.g Swaps V1). With every swap version upgrade,
        // there will always be a window of time where newer clients can receive a previously
        // created swap with an older version (scanning same ln invoice of an already created swap).
        // We decided to save a lot of trouble and code and not support this edge case. This check
        // (and this comment) makes this decision EXPLICIT :)
        Preconditions.checkArgument(fundingOutput.scriptVersion == Libwallet.AddressVersionSwapsV2);

        // Check that the embedded invoice is the same as the original
        if (!originalInvoice.equalsIgnoreCase(swapJson.invoice)) {
            return false;
        }

        // Parse invoice
        final LnInvoice invoice = LnInvoice.decode(network, originalInvoice);

        // Check that the receiver is the same as the original
        if (!invoice.destinationPubKey.equals(swapJson.receiver.publicKey)) {
            return false;
        }

        // Check that the payment hash matches the invoice
        if (!invoice.id.equals(fundingOutput.serverPaymentHashInHex)) {
            return false;
        }

        if (!originalExpirationInBlocks.equals(fundingOutput.expirationInBlocks)) {
            return false;
        }

        final PublicKey userPublicKey = PublicKey.fromJson(fundingOutput.userPublicKey);
        final PublicKey muunPublicKey = PublicKey.fromJson(fundingOutput.muunPublicKey);

        final PublicKeyPair derivedPublicKeyPair = userPublicKeyPair
                .deriveFromAbsolutePath(fundingOutput.userPublicKey.path);

        // Check that the user public key belongs to the user
        if (!derivedPublicKeyPair.getUserPublicKey().equals(userPublicKey)) {
            return false;
        }

        // Check that the muun public key belongs to muun
        if (!derivedPublicKeyPair.getMuunPublicKey().equals(muunPublicKey)) {
            return false;
        }

        final String paymentHashInHex = fundingOutput.serverPaymentHashInHex;

        // Check that the witness script was computed according to the given parameters
        final byte[] witnessScript = createWitnessScript(
                Encodings.hexToBytes(paymentHashInHex),
                userPublicKey.getPublicKeyBytes(),
                muunPublicKey.getPublicKeyBytes(),
                Encodings.hexToBytes(fundingOutput.serverPublicKeyInHex),
                fundingOutput.expirationInBlocks
        );

        // Check that the script hashes to the output address we'll be using
        final Address outputAddress = createAddress(network, witnessScript);

        if (!outputAddress.toString().equals(fundingOutput.outputAddress)) {
            return false;
        }

        // Check other values for internal consistency
        final String preimageInHex = swapJson.preimageInHex;
        if (preimageInHex != null) {

            final byte[] calculatedHash = Hashes.sha256(Encodings.hexToBytes(preimageInHex));
            if (!paymentHashInHex.equals(Encodings.bytesToHex(calculatedHash))) {
                return false;
            }
        }

        return true;
    }


    /**
     * Create the witness script for spending the submarine swap output.
     */
    public static byte[] createWitnessScript(byte[] swapPaymentHash256,
                                             byte[] userPublicKey,
                                             byte[] muunPublicKey,
                                             byte[] swapServerPublicKey,
                                             int numBlocksForExpiration) {

        // per bip 68 (the one where relative lock-time is defined)
        final int max_relative_lock_time_blocks = 0xFFFF;
        Preconditions.checkArgument(numBlocksForExpiration <= max_relative_lock_time_blocks);

        // It turns out that the payment hash present in an invoice is just the SHA256 of the
        // payment preimage, so we still have to do a pass of RIPEMD160 before pushing it to the
        // script
        final byte[] swapPaymentHash160 = Hashes.ripemd160(swapPaymentHash256);
        final byte[] serverPublicKeyHash160 = Hashes.sha256Ripemd160(muunPublicKey);

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

        return new ScriptBuilder()
                // Push the user public key to the second position of the stack
                .data(userPublicKey)
                .op(OP_SWAP)

                // Check whether the first stack item was a valid swap server signature
                .data(swapServerPublicKey)
                .op(OP_CHECKSIG)

                // If the swap server signature was correct
                .op(OP_IF)
                .op(OP_SWAP)

                // Check whether the second stack item was the payment preimage
                .op(OP_DUP)
                .op(OP_HASH160)
                .data(swapPaymentHash160)
                .op(OP_EQUAL)

                // If the preimage was correct
                .op(OP_IF)
                // We are done, leave just one true-ish item in the stack (there're 2
                // remaining items)
                .op(OP_DROP)

                // If the second stack item wasn't a valid payment preimage
                .op(OP_ELSE)
                // Validate that the second stack item was a valid user signature
                .op(OP_SWAP)
                .op(OP_CHECKSIG)

                .op(OP_ENDIF)

                // If the first stack item wasn't a valid server signature
                .op(OP_ELSE)
                // Validate that the blockchain height is big enough
                .number(numBlocksForExpiration)
                .op(OP_CHECKSEQUENCEVERIFY)
                .op(OP_DROP)

                // Validate that the second stack item was a valid user signature
                .op(OP_CHECKSIGVERIFY)

                // Validate that the third stack item was the muun public key
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
                .getProgram();
    }

    /**
     * Create an address.
     */
    public static Address createAddress(NetworkParameters network, byte[] witnessScript) {

        final byte[] witnessScriptHash = Sha256Hash.hash(witnessScript);
        return SegwitAddress.fromHash(network, witnessScriptHash);
    }
}
