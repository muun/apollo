package io.muun.common.api;

import io.muun.common.crypto.ChallengePublicKey;
import io.muun.common.crypto.hd.MuunAddress;
import io.muun.common.crypto.hd.MuunInput;
import io.muun.common.crypto.hd.MuunOutput;
import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.crypto.hd.Signature;
import io.muun.common.model.challenge.Challenge;
import io.muun.common.model.challenge.ChallengeSetup;
import io.muun.common.model.challenge.ChallengeSignature;
import io.muun.common.utils.Encodings;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

public class CommonModelObjectsMapper {

    protected final NetworkParameters networkParameters;

    /**
     * Constructor.
     */
    public CommonModelObjectsMapper(NetworkParameters networkParameters) {
        this.networkParameters = networkParameters;
    }

    /**
     * Create a Signature.
     */
    @NotNull
    public Signature mapSignature(SignatureJson signatureJson) {
        if (signatureJson == null) {
            return null;
        }

        return new Signature(Encodings.hexToBytes(signatureJson.hex));
    }

    /**
     * Create a MuunAddress.
     */
    @NotNull
    public MuunAddress mapMuunAddress(MuunAddressJson addressJson) {
        return new MuunAddress(
                addressJson.version,
                addressJson.derivationPath,
                addressJson.address
        );
    }

    /**
     * Create a MuunInput.
     */
    @NotNull
    public MuunInput mapMuunInput(MuunInputJson inputJson) {
        return new MuunInput(
                mapMuunOutput(inputJson.prevOut),
                mapMuunAddress(inputJson.address),
                mapSignature(inputJson.userSignature),
                mapSignature(inputJson.muunSignature)
        );
    }

    /**
     * Create a MuunOutput.
     */
    public MuunOutput mapMuunOutput(MuunOutputJson outputJson) {
        return new MuunOutput(
                outputJson.txId,
                outputJson.index,
                outputJson.amount
        );
    }

    /**
     * Create a PartiallySignedTransaction.
     */
    @NotNull
    public io.muun.common.crypto.tx.PartiallySignedTransaction mapPartiallySignedTransaction(
            io.muun.common.api.PartiallySignedTransaction partiallySignedTransaction) {

        final byte[] binaryTransaction = Encodings
                .hexToBytes(partiallySignedTransaction.hexTransaction);

        final org.bitcoinj.core.Transaction
                transaction = new Transaction(networkParameters, binaryTransaction);

        final List<MuunInputJson> inputJsons = partiallySignedTransaction.inputs;

        final List<MuunInput> inputs = new ArrayList<>(inputJsons.size());
        for (MuunInputJson inputJson: inputJsons) {
            inputs.add(mapMuunInput(inputJson));
        }

        return new io.muun.common.crypto.tx.PartiallySignedTransaction(transaction, inputs);
    }


    /**
     * Create a PublicKey.
     */
    @Nullable
    public PublicKey mapPublicKey(@Nullable PublicKeyJson publicKey) {
        if (publicKey == null) {
            return null;
        }

        return PublicKey.deserializeFromBase58(publicKey.path, publicKey.key);
    }


    /**
     * Create a Challenge.
     */
    public Challenge mapChallenge(ChallengeJson challengeJson) {
        if (challengeJson == null) {
            return null;
        }

        return new Challenge(
                challengeJson.type,
                Encodings.hexToBytes(challengeJson.challenge),
                Encodings.hexToBytes(challengeJson.salt)
        );
    }

    /**
     * Create a ChallengePublicKey.
     */
    public ChallengePublicKey mapChallengePublicKey(String challengePublicKeyHex) {
        if (challengePublicKeyHex == null) {
            return null;
        }

        return ChallengePublicKey.fromBytes(Encodings.hexToBytes(challengePublicKeyHex));
    }

    /**
     * Create a ChallengeSetup.
     */
    public ChallengeSetup mapChallengeSetup(ChallengeSetupJson challengeSetupJson) {
        if (challengeSetupJson == null) {
            return null;
        }

        return new ChallengeSetup(
                challengeSetupJson.type,
                mapChallengePublicKey(challengeSetupJson.publicKey),
                Encodings.hexToBytes(challengeSetupJson.salt),
                challengeSetupJson.encryptedPrivateKey,
                challengeSetupJson.version
        );
    }

    /**
     * Create a ChallengeSignature.
     */
    public ChallengeSignature mapChallengeSignature(ChallengeSignatureJson challengeSignatureJson) {
        if (challengeSignatureJson == null) {
            return null;
        }

        return new ChallengeSignature(
                challengeSignatureJson.type,
                Encodings.hexToBytes(challengeSignatureJson.hex)
        );
    }
}
