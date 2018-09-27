package io.muun.common.crypto.tx;

import io.muun.common.crypto.hd.MuunInput;
import io.muun.common.crypto.hd.PrivateKey;
import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.crypto.hd.PublicKeyPair;
import io.muun.common.crypto.schemes.TransactionSchemeV1;
import io.muun.common.crypto.schemes.TransactionSchemeV2;
import io.muun.common.crypto.schemes.TransactionSchemeV3;
import io.muun.common.exception.MissingCaseError;
import io.muun.common.utils.Preconditions;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.script.Script;

import java.util.List;

import javax.validation.constraints.NotNull;

public class PartiallySignedTransaction {

    @NotNull
    private final Transaction transaction;

    @NotNull
    private final List<MuunInput> inputs;

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

        Preconditions.checkNotNull(input.getMuunSignature()); // never sign before Muun does.

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

        Preconditions.checkNotNull(input.getMuunSignature()); // never sign before Muun does.

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

        final Script signedScript = TransactionSchemeV3.createInputScript(publicKeyPair);

        transaction.getInput(inputIndex).setScriptSig(signedScript);

        final TransactionWitness witness = TransactionSchemeV3.createWitness(
                publicKeyPair,
                input.getUserSignature(),
                input.getMuunSignature()
        );
        transaction.setWitness(inputIndex, witness);
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
}
