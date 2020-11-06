package io.muun.common.api;

import io.muun.common.crypto.ChallengePublicKey;
import io.muun.common.crypto.hd.HardwareWalletAddress;
import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.model.challenge.Challenge;
import io.muun.common.model.challenge.ChallengeSetup;
import io.muun.common.model.challenge.ChallengeSignature;
import io.muun.common.utils.Encodings;

import org.bitcoinj.core.NetworkParameters;

import javax.annotation.Nullable;

public class CommonModelObjectsMapper {

    protected final NetworkParameters networkParameters;

    /**
     * Constructor.
     */
    public CommonModelObjectsMapper(NetworkParameters networkParameters) {
        this.networkParameters = networkParameters;
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
                challengeJson.salt == null ? null : Encodings.hexToBytes(challengeJson.salt)
        );
    }

    /**
     * Create a ChallengePublicKey.
     */
    public ChallengePublicKey mapChallengePublicKey(
            String challengePublicKeyHex,
            String challengeSaltHex,
            int version) {

        if (challengePublicKeyHex == null || challengeSaltHex == null) {
            return null;
        }

        return new ChallengePublicKey(
                Encodings.hexToBytes(challengePublicKeyHex),
                Encodings.hexToBytes(challengeSaltHex),
                version
        );
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
                mapChallengePublicKey(
                        challengeSetupJson.publicKey,
                        challengeSetupJson.salt,
                        challengeSetupJson.version
                ),
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

    /**
     * Create an HardwareWalletAddress.
     */
    public HardwareWalletAddress mapHardwareWalletAddress(HardwareWalletAddressJson json) {
        return new HardwareWalletAddress(
                json.address,
                json.derivationPath
        );
    }
}
