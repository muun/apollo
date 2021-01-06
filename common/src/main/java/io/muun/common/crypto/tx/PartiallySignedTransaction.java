package io.muun.common.crypto.tx;

import io.muun.common.api.MuunInputJson;
import io.muun.common.api.PartiallySignedTransactionJson;
import io.muun.common.crypto.hd.MuunInput;
import io.muun.common.crypto.hd.MuunInputIncomingSwap;
import io.muun.common.crypto.hd.MuunInputSubmarineSwapV101;
import io.muun.common.crypto.hd.MuunInputSubmarineSwapV102;
import io.muun.common.crypto.hd.PrivateKey;
import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.crypto.hd.PublicKeyTriple;
import io.muun.common.crypto.hd.Signature;
import io.muun.common.crypto.schemes.TransactionScheme;
import io.muun.common.crypto.schemes.TransactionSchemeIncomingSwap;
import io.muun.common.crypto.schemes.TransactionSchemeSubmarineSwap;
import io.muun.common.crypto.schemes.TransactionSchemeSubmarineSwapV2;
import io.muun.common.exception.MissingCaseError;
import io.muun.common.utils.Preconditions;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.script.Script;
import rx.Single;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import static io.muun.common.utils.Preconditions.checkNotNull;

public class PartiallySignedTransaction {

    @NotNull
    private final Transaction transaction;

    @NotNull
    private final List<MuunInput> inputs;

    /**
     * Build from a json-serializable representation.
     */
    public static PartiallySignedTransaction fromJson(
            PartiallySignedTransactionJson json,
            NetworkParameters network) {

        if (json == null) {
            return null;
        }

        final ArrayList<MuunInput> inputs = new ArrayList<>();
        for (MuunInputJson input : json.inputs) {
            inputs.add(MuunInput.fromJson(input));
        }

        return new PartiallySignedTransaction(
                TransactionHelpers.getTransactionFromHexRaw(network, json.hexTransaction),
                inputs
        );
    }

    /**
     * Constructor.
     */
    public PartiallySignedTransaction(Transaction transaction, List<MuunInput> inputs) {
        this.transaction = transaction;
        this.inputs = inputs;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public List<MuunInput> getInputs() {
        return inputs;
    }

    /**
     * Create and attach the user's Signatures to this PartiallySignedTransaction.
     */
    public void addUserSignatures(
            PrivateKey baseUserPrivateKey,
            PublicKey baseMuunPublicKey,
            PublicKey baseSwapServerPublicKey) {

        for (int index = 0; index < inputs.size(); index++) {

            final int version = inputs.get(index).getVersion();

            final TransactionScheme scheme = TransactionScheme.get(version).orElse(null);

            if (scheme != null) {

                addUserSignature(
                        scheme,
                        index,
                        baseUserPrivateKey,
                        baseMuunPublicKey,
                        baseSwapServerPublicKey
                );

            } else {

                switch (version) {

                    case TransactionSchemeSubmarineSwap.ADDRESS_VERSION:
                        addUserSignatureSubmarineSwap101(index, baseUserPrivateKey);
                        break;

                    case TransactionSchemeSubmarineSwapV2.ADDRESS_VERSION:
                        addUserSignatureSubmarineSwap102(index, baseUserPrivateKey);
                        break;

                    case TransactionSchemeIncomingSwap.ADDRESS_VERSION:
                        addUserSignatureIncomingSwap(index, baseUserPrivateKey, baseMuunPublicKey);
                        break;

                    default:
                        throw new MissingCaseError(version, "ADDRESS_VERSION");
                }
            }
        }
    }

    /**
     * Create and attach Muun's Signatures to a PartiallySignedTransaction.
     */
    public void addMuunSignatures(
            PublicKey baseUserPublicKey,
            PrivateKey baseMuunPrivateKey,
            PublicKey baseSwapServerPublicKey) {

        for (int index = 0; index < inputs.size(); index++) {

            final int version = inputs.get(index).getVersion();

            final TransactionScheme scheme = TransactionScheme.get(version).orElse(null);

            if (scheme != null) {

                if (scheme.needsMuunSignature()) {
                    addMuunSignature(
                            scheme,
                            index,
                            baseUserPublicKey,
                            baseMuunPrivateKey,
                            baseSwapServerPublicKey
                    );
                }

            } else {

                switch (version) {
                    case TransactionSchemeSubmarineSwap.ADDRESS_VERSION:
                    case TransactionSchemeSubmarineSwapV2.ADDRESS_VERSION:
                        break; // Houston doesn't sign this. Swap server will, when sweeping.

                    case TransactionSchemeIncomingSwap.ADDRESS_VERSION:
                        addMuunSignatureIncomingSwap(index, baseUserPublicKey, baseMuunPrivateKey);
                        break;

                    default:
                        throw new MissingCaseError(version, "ADDRESS_VERSION");
                }
            }
        }
    }

    /**
     * Create and attach the swap server signatures to a partially signed transaction.
     */
    public void addSwapServerSignatures(Signer signer) {

        for (int index = 0; index < inputs.size(); index++) {

            final int version = inputs.get(index).getVersion();

            final TransactionScheme scheme = TransactionScheme.get(version).orElse(null);

            if (scheme != null) {

                if (scheme.needsSwapServerSignature()) {
                    addSwapServerSignature(
                            scheme,
                            index,
                            signer
                    );
                }

            } else {

                switch (version) {
                    case TransactionSchemeSubmarineSwap.ADDRESS_VERSION:
                    case TransactionSchemeIncomingSwap.ADDRESS_VERSION:
                        break; // The swap server doesn't sign this

                    case TransactionSchemeSubmarineSwapV2.ADDRESS_VERSION:
                        addSwapServerSignatureSubmarineSwap102(index, signer);
                        break;

                    default:
                        throw new MissingCaseError(version, "ADDRESS_VERSION");
                }
            }
        }
    }

    private void addUserSignature(
            TransactionScheme scheme,
            int inputIndex,
            PrivateKey baseUserPrivateKey,
            PublicKey baseMuunPublicKey,
            PublicKey baseSwapServerPublicKey) {

        final MuunInput input = inputs.get(inputIndex);
        final String derivationPath = input.getDerivationPath();

        // never sign before Muun does
        if (scheme.needsMuunSignature()) {
            checkNotNull(input.getMuunSignature());
        }

        // never sign before Muun does
        if (scheme.needsSwapServerSignature()) {
            checkNotNull(input.getSwapServerSignature());
        }

        final PrivateKey userPrivateKey = baseUserPrivateKey.deriveFromAbsolutePath(derivationPath);
        final PublicKey muunPublicKey = baseMuunPublicKey.deriveFromAbsolutePath(derivationPath);
        final PublicKey swapServerPublicKey = baseSwapServerPublicKey.deriveFromAbsolutePath(
                derivationPath
        );

        final PublicKeyTriple publicKeyTriple = new PublicKeyTriple(
                userPrivateKey.getPublicKey(),
                muunPublicKey,
                swapServerPublicKey
        );

        final byte[] dataToSign = scheme.createDataToSignInput(
                transaction,
                inputIndex,
                input.getPrevOut().getAmount(),
                publicKeyTriple
        );

        input.setUserSignature(userPrivateKey.signTransactionHash(dataToSign));

        final Script inputScript = scheme.createInputScript(
                publicKeyTriple,
                input.getUserSignature(),
                input.getMuunSignature(),
                input.getSwapServerSignature()
        );

        transaction.getInput(inputIndex).setScriptSig(inputScript);

        final TransactionWitness witness = scheme.createWitness(
                publicKeyTriple,
                input.getUserSignature(),
                input.getMuunSignature(),
                input.getSwapServerSignature()
        );

        transaction.getInput(inputIndex).setWitness(witness);
    }

    private void addMuunSignature(
            TransactionScheme scheme,
            int inputIndex,
            PublicKey baseUserPublicKey,
            PrivateKey baseMuunPrivateKey,
            PublicKey baseSwapServerPublicKey) {

        final MuunInput input = inputs.get(inputIndex);
        final String derivationPath = input.getDerivationPath();

        // always sign before the user does
        Preconditions.checkNull(input.getUserSignature());

        final PrivateKey muunPrivateKey = baseMuunPrivateKey.deriveFromAbsolutePath(derivationPath);
        final PublicKey userPublicKey = baseUserPublicKey.deriveFromAbsolutePath(derivationPath);
        final PublicKey swapServerPublicKey = baseSwapServerPublicKey.deriveFromAbsolutePath(
                derivationPath
        );

        final PublicKeyTriple publicKeyTriple = new PublicKeyTriple(
                userPublicKey,
                muunPrivateKey.getPublicKey(),
                swapServerPublicKey
        );

        final byte[] dataToSign = scheme.createDataToSignInput(
                transaction,
                inputIndex,
                input.getPrevOut().getAmount(),
                publicKeyTriple
        );

        input.setMuunSignature(muunPrivateKey.signTransactionHash(dataToSign));
    }

    private void addSwapServerSignature(TransactionScheme scheme, int inputIndex, Signer signer) {

        final MuunInput input = inputs.get(inputIndex);

        final String hexRawTransaction = TransactionHelpers.getHexRawTransaction(transaction);

        final Signature signature = signer.signSwapRefund(
                null,
                input.getVersion(),
                hexRawTransaction,
                inputIndex
        )
                .toBlocking()
                .value();

        input.setSwapServerSignature(signature);
    }

    private void addUserSignatureSubmarineSwap101(int inputIndex,
                                                  PrivateKey baseUserPrivateKey) {

        final MuunInput input = inputs.get(inputIndex);

        final MuunInputSubmarineSwapV101 submarineSwap = input.getSubmarineSwap();
        checkNotNull(submarineSwap);

        final PrivateKey userPrivateKey = baseUserPrivateKey
                .deriveFromAbsolutePath(input.getDerivationPath());

        final byte[] witnessScript = TransactionSchemeSubmarineSwap.createWitnessScript(
                submarineSwap.getRefundAddress(),
                submarineSwap.getSwapPaymentHash256(),
                submarineSwap.getSwapServerPublicKey(),
                submarineSwap.getLockTime()
        );

        final byte[] dataToSign = TransactionSchemeSubmarineSwap.createDataToSignInput(
                transaction,
                inputIndex,
                input.getPrevOut().getAmount(),
                witnessScript
        );

        final Signature userSignature = userPrivateKey.signTransactionHash(dataToSign);
        input.setUserSignature(userSignature);

        final TransactionWitness witness = TransactionSchemeSubmarineSwap.createWitnessForUser(
                userPrivateKey.getPublicKey(),
                userSignature,
                witnessScript
        );

        final Script inputScript = TransactionSchemeSubmarineSwap.createInputScript(witnessScript);

        transaction.getInput(inputIndex).setScriptSig(inputScript);
        transaction.getInput(inputIndex).setWitness(witness);
    }

    private void addUserSignatureSubmarineSwap102(
            int inputIndex,
            PrivateKey baseUserPrivateKey) {

        final MuunInput input = inputs.get(inputIndex);

        final MuunInputSubmarineSwapV102 swap = checkNotNull(input.getSubmarineSwapV102());

        final byte[] witnessScript = swap.getWitnessScript();

        final byte[] dataToSign = TransactionSchemeSubmarineSwapV2.createDataToSignInput(
                transaction,
                inputIndex,
                input.getPrevOut().getAmount(),
                witnessScript
        );

        final PrivateKey userPrivateKey = baseUserPrivateKey
                .deriveFromAbsolutePath(input.getDerivationPath());

        final Signature userSignature = userPrivateKey.signTransactionHash(dataToSign);
        input.setUserSignature(userSignature);

        final TransactionWitness witness = TransactionSchemeSubmarineSwapV2
                .createWitnessForCollaboration(
                        userSignature,
                        checkNotNull(swap.getSwapServerSignature()),
                        witnessScript
                );

        transaction.getInput(inputIndex).setWitness(witness);
    }

    private void addSwapServerSignatureSubmarineSwap102(int inputIndex, Signer signer) {

        final MuunInput input = inputs.get(inputIndex);

        final MuunInputSubmarineSwapV102 swap = checkNotNull(input.getSubmarineSwapV102());

        final String hexRawTransaction = TransactionHelpers.getHexRawTransaction(transaction);

        final Signature signature = signer.signSwapRefund(
                swap.getSwapUuid(),
                input.getVersion(),
                hexRawTransaction,
                inputIndex
        )
                .toBlocking()
                .value();

        swap.setSwapServerSignature(signature);
    }

    private void addUserSignatureIncomingSwap(final int inputIndex,
                                              final PrivateKey baseUserPrivateKey,
                                              final PublicKey baseMuunPublicKey) {

        final MuunInput input = inputs.get(inputIndex);
        final String derivationPath = input.getDerivationPath();

        Preconditions.checkNotNull(input.getMuunSignature()); // always sign after the houston does.

        final PublicKey muunPublicKey = baseMuunPublicKey.deriveFromAbsolutePath(derivationPath);
        final PrivateKey userPrivateKey = baseUserPrivateKey.deriveFromAbsolutePath(derivationPath);

        // Add the user's signature to the PartiallySignedTransaction:
        final MuunInputIncomingSwap incomingSwap = input.getIncomingSwap();
        final byte[]  witnessScript = TransactionSchemeIncomingSwap.createWitnessScript(
                incomingSwap.getPaymentHash256(),
                userPrivateKey.getPublicKey().getPublicKeyBytes(),
                muunPublicKey.getPublicKeyBytes(),
                incomingSwap.getSwapServerPublicKey(),
                incomingSwap.getExpirationHeight()
        );
        final byte[] dataToSign = TransactionSchemeIncomingSwap.createDataToSignInput(
                transaction,
                inputIndex,
                input.getPrevOut().getAmount(),
                witnessScript
        );

        input.setUserSignature(userPrivateKey.signTransactionHash(dataToSign));
    }

    private void addMuunSignatureIncomingSwap(final int inputIndex,
                                              final PublicKey baseUserPublicKey,
                                              final PrivateKey baseMuunPrivateKey) {

        final MuunInput input = inputs.get(inputIndex);
        final String derivationPath = input.getDerivationPath();

        Preconditions.checkNull(input.getUserSignature()); // always sign before the user does.

        final PrivateKey muunPrivateKey = baseMuunPrivateKey.deriveFromAbsolutePath(derivationPath);
        final PublicKey userPublicKey = baseUserPublicKey.deriveFromAbsolutePath(derivationPath);

        // Add the user's signature to the PartiallySignedTransaction:
        final MuunInputIncomingSwap incomingSwap = input.getIncomingSwap();
        final byte[]  witnessScript = TransactionSchemeIncomingSwap.createWitnessScript(
                incomingSwap.getPaymentHash256(),
                userPublicKey.getPublicKeyBytes(),
                muunPrivateKey.getPublicKey().getPublicKeyBytes(),
                incomingSwap.getSwapServerPublicKey(),
                incomingSwap.getExpirationHeight()
        );
        final byte[] dataToSign = TransactionSchemeIncomingSwap.createDataToSignInput(
                transaction,
                inputIndex,
                input.getPrevOut().getAmount(),
                witnessScript
        );

        input.setMuunSignature(muunPrivateKey.signTransactionHash(dataToSign));
    }

    /**
     * Convert to a json-serializable representation.
     */
    public PartiallySignedTransactionJson toJson() {

        final ArrayList<MuunInputJson> jsonInputs = new ArrayList<>();
        for (MuunInput input : inputs) {
            jsonInputs.add(input.toJson());
        }

        return new PartiallySignedTransactionJson(
                TransactionHelpers.getHexRawTransaction(transaction),
                jsonInputs
        );
    }

    public interface Signer {
        Single<Signature> signSwapRefund(
                @Nullable String swapUuid,
                Integer scriptVersion,
                String hexTransaction,
                int inputIndex
        );
    }
}
