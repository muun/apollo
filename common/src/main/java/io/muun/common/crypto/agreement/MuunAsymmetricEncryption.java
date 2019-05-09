package io.muun.common.crypto.agreement;

import io.muun.common.crypto.ChallengePrivateKey;
import io.muun.common.crypto.ChallengePublicKey;
import io.muun.common.crypto.Cryptography;
import io.muun.common.crypto.CryptographyException;
import io.muun.common.crypto.hd.PrivateKey;

import org.spongycastle.crypto.CryptoException;
import org.spongycastle.pqc.math.linearalgebra.ByteUtils;

import javax.crypto.SecretKey;

public class MuunAsymmetricEncryption {

    /**
     * @param privateKey from which the deterministic private key and chain code will be encrypted.
     * @param key1       to be used for cyphering the message.
     * @param key2       to be used for cyphering the message.
     * @return a `byte[]` encoded in ASN.1 using `MuunSerializedMessage` schema.
     */
    public static String encryptWith(
            PrivateKey privateKey,
            ChallengePublicKey key1,
            ChallengePublicKey key2
    ) {

        final SecretKeyContainer secretKeyContainer =
                SharedSecretAgreement.generateSecretAesKeyFromPublicKeys(
                        key1,
                        key2
                );

        final byte[] message = ByteUtils.concatenate(
                privateKey.getPrivKey32(),
                privateKey.getChainCode()
        );

        final byte[] cypherText;

        try {
            cypherText = Cryptography.aesCbcNoPaddingEncrypt(
                    message,
                    secretKeyContainer.publicKey.generateIv(),
                    secretKeyContainer.sharedSecretKey
            );
        } catch (CryptoException e) {
            throw new CryptographyException(e);
        }

        return MuunSerializedMessage.create(
                secretKeyContainer.publicKey,
                cypherText
        ).toBase58();
    }

    /**
     * @param cypherText  to decrypt.
     * @param privateKey1 to be used for decrypting the message.
     * @param privateKey2 to be used for decrypting the message.
     * @return a `byte[]` with the original message.
     */
    public static byte[] decryptFrom(
            String cypherText,
            ChallengePrivateKey privateKey1,
            ChallengePrivateKey privateKey2) {

        final MuunSerializedMessage container = MuunSerializedMessage.fromBase58(cypherText);

        final SecretKey secretAesDecryptKey = SharedSecretAgreement.extractSecretFromRemotePublic(
                container.publicKey,
                privateKey1,
                privateKey2
        );

        try {
            return Cryptography.aesCbcNoPaddingDecrypt(
                    container.cypherText,
                    secretAesDecryptKey,
                    container.publicKey.generateIv()
            );
        } catch (CryptoException e) {
            throw new CryptographyException(e);
        }
    }

}
