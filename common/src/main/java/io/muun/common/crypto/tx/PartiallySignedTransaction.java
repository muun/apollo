package io.muun.common.crypto.tx;

import io.muun.common.api.MuunInputJson;
import io.muun.common.api.PartiallySignedTransactionJson;
import io.muun.common.crypto.hd.MuunInput;
import io.muun.common.crypto.hd.MuunInputIncomingSwap;
import io.muun.common.crypto.hd.MuunInputSubmarineSwapV101;
import io.muun.common.crypto.hd.MuunInputSubmarineSwapV102;
import io.muun.common.crypto.hd.PrivateKey;
import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.crypto.hd.PublicKeyPair;
import io.muun.common.crypto.hd.Signature;
import io.muun.common.crypto.schemes.TransactionSchemeIncomingSwap;
import io.muun.common.crypto.schemes.TransactionSchemeSubmarineSwap;
import io.muun.common.crypto.schemes.TransactionSchemeSubmarineSwapV2;
import io.muun.common.crypto.schemes.TransactionSchemeV1;
import io.muun.common.crypto.schemes.TransactionSchemeV2;
import io.muun.common.crypto.schemes.TransactionSchemeV3;
import io.muun.common.crypto.schemes.TransactionSchemeV4;
import io.muun.common.exception.MissingCaseError;
import io.muun.common.utils.Preconditions;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.script.Script;
import rx.Single;
import rx.functions.Func3;

import java.util.ArrayList;
import java.util.List;
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
    public void addUserSignatures(PrivateKey baseUserPrivateKey, PublicKey baseMuunPublicKey) {
        for (int i = 0; i < inputs.size(); i++) {
            final int version = inputs.get(i).getVersion();

            switch (version) {
                case TransactionSchemeV1.ADDRESS_VERSION:
                    addUserSignatureV1(i, baseUserPrivateKey);
                    break;

                case TransactionSchemeV2.ADDRESS_VERSION:
                    addUserSignatureV2(i, baseUserPrivateKey, baseMuunPublicKey);
                    break;

                case TransactionSchemeV3.ADDRESS_VERSION:
                    addUserSignatureV3(i, baseUserPrivateKey, baseMuunPublicKey);
                    break;

                case TransactionSchemeV4.ADDRESS_VERSION:
                    addUserSignatureV4(i, baseUserPrivateKey, baseMuunPublicKey);
                    break;

                case TransactionSchemeSubmarineSwap.ADDRESS_VERSION:
                    addUserSignatureSubmarineSwap101(i, baseUserPrivateKey);
                    break;

                case TransactionSchemeSubmarineSwapV2.ADDRESS_VERSION:
                    addUserSignatureSubmarineSwap102(i, baseUserPrivateKey);
                    break;

                case TransactionSchemeIncomingSwap.ADDRESS_VERSION:
                    addUserSignatureIncomingSwap(i, baseUserPrivateKey, baseMuunPublicKey);
                    break;

                default:
                    throw new MissingCaseError(version, "ADDRESS_VERSION");
            }
        }
    }

    /**
     * Create and attach Muun's Signatures to a PartiallySignedTransaction.
     */
    public void addMuunSignatures(PublicKey baseUserPublicKey, PrivateKey baseMuunPrivateKey) {
        for (int i = 0; i < inputs.size(); i++) {
            final int version = inputs.get(i).getVersion();

            switch (version) {
                case TransactionSchemeV1.ADDRESS_VERSION:
                    break; // Houston doesn't sign V1 inputs

                case TransactionSchemeV2.ADDRESS_VERSION:
                    addMuunSignatureV2(i, baseUserPublicKey, baseMuunPrivateKey);
                    break;

                case TransactionSchemeV3.ADDRESS_VERSION:
                    addMuunSignatureV3(i, baseUserPublicKey, baseMuunPrivateKey);
                    break;

                case TransactionSchemeV4.ADDRESS_VERSION:
                    addMuunSignatureV4(i, baseUserPublicKey, baseMuunPrivateKey);
                    break;

                case TransactionSchemeSubmarineSwap.ADDRESS_VERSION:
                case TransactionSchemeSubmarineSwapV2.ADDRESS_VERSION:
                    break; // Houston doesn't sign this. Swap server will, when sweeping.

                case TransactionSchemeIncomingSwap.ADDRESS_VERSION:
                    addMuunSignatureIncomingSwap(i, baseUserPublicKey, baseMuunPrivateKey);
                    break;

                default:
                    throw new MissingCaseError(version, "ADDRESS_VERSION");
            }
        }
    }

    /**
     * Create and attach the swap server signatures to a partially signed transaction.
     */
    public void addSwapServerSignatures(Func3<String, String, Integer, Single<Signature>> signer) {

        for (int i = 0; i < inputs.size(); i++) {

            final int version = inputs.get(i).getVersion();

            switch (version) {
                case TransactionSchemeV1.ADDRESS_VERSION:
                case TransactionSchemeV2.ADDRESS_VERSION:
                case TransactionSchemeV3.ADDRESS_VERSION:
                case TransactionSchemeV4.ADDRESS_VERSION:
                case TransactionSchemeSubmarineSwap.ADDRESS_VERSION:
                case TransactionSchemeIncomingSwap.ADDRESS_VERSION:
                    break; // The swap server doesn't sign this

                case TransactionSchemeSubmarineSwapV2.ADDRESS_VERSION:
                    addSwapServerSignatureSubmarineSwap102(i, signer);
                    break;

                default:
                    throw new MissingCaseError(version, "ADDRESS_VERSION");
            }
        }
    }

    private void addUserSignatureV1(int inputIndex, PrivateKey baseUserKey) {
        final MuunInput input = inputs.get(inputIndex);

        final PrivateKey userPrivateKey = baseUserKey
                .deriveFromAbsolutePath(input.getDerivationPath());

        final byte[] dataToSign = TransactionSchemeV1.createDataToSignInput(
                transaction,
                inputIndex,
                userPrivateKey.getPublicKey()
        );

        input.setUserSignature(userPrivateKey.signTransactionHash(dataToSign));

        final Script signedScript = TransactionSchemeV1.createInputScript(
                userPrivateKey.getPublicKey(),
                input.getUserSignature()
        );

        transaction.getInput(inputIndex).setScriptSig(signedScript);
    }

    private void addUserSignatureV2(int inputIndex,
                                    PrivateKey baseUserPrivateKey,
                                    PublicKey baseMuunPublicKey) {

        final MuunInput input = inputs.get(inputIndex);
        final String derivationPath = input.getDerivationPath();

        checkNotNull(input.getMuunSignature()); // never sign before Muun does.

        final PrivateKey userPrivateKey = baseUserPrivateKey.deriveFromAbsolutePath(derivationPath);
        final PublicKey muunPublicKey = baseMuunPublicKey.deriveFromAbsolutePath(derivationPath);

        final PublicKeyPair publicKeyPair = new PublicKeyPair(
                userPrivateKey.getPublicKey(),
                muunPublicKey
        );

        final byte[] dataToSign = TransactionSchemeV2.createDataToSignInput(
                transaction,
                inputIndex,
                publicKeyPair
        );

        input.setUserSignature(userPrivateKey.signTransactionHash(dataToSign));

        final Script signedScript = TransactionSchemeV2.createInputScript(
                publicKeyPair,
                input.getUserSignature(),
                input.getMuunSignature()
        );

        transaction.getInput(inputIndex).setScriptSig(signedScript);
    }

    private void addMuunSignatureV2(int inputIndex,
                                    PublicKey baseUserPublicKey,
                                    PrivateKey baseMuunPrivateKey) {

        final MuunInput input = inputs.get(inputIndex);
        final String derivationPath = input.getDerivationPath();

        Preconditions.checkNull(input.getUserSignature()); // always sign before the user does.

        final PrivateKey muunPrivateKey = baseMuunPrivateKey.deriveFromAbsolutePath(derivationPath);
        final PublicKey userPublicKey = baseUserPublicKey.deriveFromAbsolutePath(derivationPath);

        final PublicKeyPair publicKeyPair = new PublicKeyPair(
                userPublicKey,
                muunPrivateKey.getPublicKey()
        );

        // Add the user's signature to the PartiallySignedTransaction:
        final byte[] dataToSign = TransactionSchemeV2.createDataToSignInput(
                transaction,
                inputIndex,
                publicKeyPair
        );

        input.setMuunSignature(muunPrivateKey.signTransactionHash(dataToSign));
    }

    private void addUserSignatureV3(int inputIndex,
                                    PrivateKey baseUserPrivateKey,
                                    PublicKey baseMuunPublicKey) {

        final MuunInput input = inputs.get(inputIndex);
        final String derivationPath = input.getDerivationPath();

        checkNotNull(input.getMuunSignature()); // never sign before Muun does.

        final PrivateKey userPrivateKey = baseUserPrivateKey.deriveFromAbsolutePath(derivationPath);
        final PublicKey muunPublicKey = baseMuunPublicKey.deriveFromAbsolutePath(derivationPath);

        final PublicKeyPair publicKeyPair = new PublicKeyPair(
                userPrivateKey.getPublicKey(),
                muunPublicKey
        );

        final byte[] dataToSign = TransactionSchemeV3.createDataToSignInput(
                transaction,
                inputIndex,
                input.getPrevOut().getAmount(),
                publicKeyPair
        );

        input.setUserSignature(userPrivateKey.signTransactionHash(dataToSign));

        final Script inputScript = TransactionSchemeV3.createInputScript(publicKeyPair);

        transaction.getInput(inputIndex).setScriptSig(inputScript);

        final TransactionWitness witness = TransactionSchemeV3.createWitness(
                publicKeyPair,
                input.getUserSignature(),
                input.getMuunSignature()
        );
        transaction.getInput(inputIndex).setWitness(witness);
    }

    private void addMuunSignatureV3(int inputIndex,
                                    PublicKey baseUserPublicKey,
                                    PrivateKey baseMuunPrivateKey) {

        final MuunInput input = inputs.get(inputIndex);
        final String derivationPath = input.getDerivationPath();

        Preconditions.checkNull(input.getUserSignature()); // always sign before the user does.

        final PrivateKey muunPrivateKey = baseMuunPrivateKey.deriveFromAbsolutePath(derivationPath);
        final PublicKey userPublicKey = baseUserPublicKey.deriveFromAbsolutePath(derivationPath);

        final PublicKeyPair publicKeyPair = new PublicKeyPair(
                userPublicKey,
                muunPrivateKey.getPublicKey()
        );

        // Add the user's signature to the PartiallySignedTransaction:
        final byte[] dataToSign = TransactionSchemeV3.createDataToSignInput(
                transaction,
                inputIndex,
                input.getPrevOut().getAmount(),
                publicKeyPair
        );

        input.setMuunSignature(muunPrivateKey.signTransactionHash(dataToSign));
    }

    private void addUserSignatureV4(int inputIndex,
                                    PrivateKey baseUserPrivateKey,
                                    PublicKey baseMuunPublicKey) {

        final MuunInput input = inputs.get(inputIndex);
        final String derivationPath = input.getDerivationPath();

        checkNotNull(input.getMuunSignature()); // never sign before Muun does.

        final PrivateKey userPrivateKey = baseUserPrivateKey.deriveFromAbsolutePath(derivationPath);
        final PublicKey muunPublicKey = baseMuunPublicKey.deriveFromAbsolutePath(derivationPath);

        final PublicKeyPair publicKeyPair = new PublicKeyPair(
                userPrivateKey.getPublicKey(),
                muunPublicKey
        );

        final byte[] dataToSign = TransactionSchemeV4.createDataToSignInput(
                transaction,
                inputIndex,
                input.getPrevOut().getAmount(),
                publicKeyPair
        );

        input.setUserSignature(userPrivateKey.signTransactionHash(dataToSign));

        final TransactionWitness witness = TransactionSchemeV4.createWitness(
                publicKeyPair,
                input.getUserSignature(),
                input.getMuunSignature()
        );
        transaction.getInput(inputIndex).setWitness(witness);
    }

    private void addMuunSignatureV4(int inputIndex,
                                    PublicKey baseUserPublicKey,
                                    PrivateKey baseMuunPrivateKey) {

        final MuunInput input = inputs.get(inputIndex);
        final String derivationPath = input.getDerivationPath();

        Preconditions.checkNull(input.getUserSignature()); // always sign before the user does.

        final PrivateKey muunPrivateKey = baseMuunPrivateKey.deriveFromAbsolutePath(derivationPath);
        final PublicKey userPublicKey = baseUserPublicKey.deriveFromAbsolutePath(derivationPath);

        final PublicKeyPair publicKeyPair = new PublicKeyPair(
                userPublicKey,
                muunPrivateKey.getPublicKey()
        );

        // Add the user's signature to the PartiallySignedTransaction:
        final byte[] dataToSign = TransactionSchemeV4.createDataToSignInput(
                transaction,
                inputIndex,
                input.getPrevOut().getAmount(),
                publicKeyPair
        );

        input.setMuunSignature(muunPrivateKey.signTransactionHash(dataToSign));
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

    private void addSwapServerSignatureSubmarineSwap102(
            int inputIndex,
            Func3<String, String, Integer, Single<Signature>> signer) {

        final MuunInput input = inputs.get(inputIndex);

        final MuunInputSubmarineSwapV102 swap = checkNotNull(input.getSubmarineSwapV102());

        final String hexRawTransaction = TransactionHelpers.getHexRawTransaction(transaction);

        final Signature signature = signer.call(swap.getSwapUuid(), hexRawTransaction, inputIndex)
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
}
