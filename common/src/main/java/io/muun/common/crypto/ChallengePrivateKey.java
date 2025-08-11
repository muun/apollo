package io.muun.common.crypto;

import io.muun.common.crypto.hd.PrivateKey;
import io.muun.common.utils.Encodings;
import io.muun.common.utils.Hashes;
import io.muun.common.utils.Preconditions;

import com.google.common.annotations.VisibleForTesting;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.bouncycastle.jcajce.provider.asymmetric.ec.KeyFactorySpi;

import java.io.IOException;
import java.math.BigInteger;
import java.security.PublicKey;
import java.util.Arrays;
import javax.crypto.SecretKey;
import javax.validation.constraints.NotNull;

public class ChallengePrivateKey {

    private final ECKey key;

    private final byte[] salt;

    private final int version;

    /**
     * Constructor.
     */
    private ChallengePrivateKey(ECKey key, byte[] salt, int version) {

        Preconditions.checkArgument(salt.length == 8);

        this.key = key;
        this.salt = salt;
        this.version = version;
    }

    /**
     * Sign a given data with the private key.
     */
    public byte[] sign(byte[] data) {
        final byte[] hash = Hashes.sha256(data);
        final ECKey.ECDSASignature signature = key.sign(Sha256Hash.wrap(hash));

        return signature.encodeToDER();
    }

    public ChallengePublicKey getChallengePublicKey() {
        return new ChallengePublicKey(key.getPubKey(), salt, version);
    }

    /**
     * Generates an EC private key instance from a `BigInteger` private key.
     *
     * @return an EC private key.
     */
    @NotNull
    private BCECPrivateKey getPrivateKey() {
        try {

            final PrivateKeyInfo privateKeyInfo = PrivateKeyInfoFactory.createPrivateKeyInfo(
                    new ECPrivateKeyParameters(
                            key.getPrivKey(),
                            ECKey.CURVE
                    )
            );

            return (BCECPrivateKey) new KeyFactorySpi.ECDH().generatePrivate(privateKeyInfo);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Constructs a password private key from a password.
     */
    public static ChallengePrivateKey fromUserInput(String text, byte[] salt, int version) {

        final byte[] inputBytes = Encodings.stringToBytes(text);
        final byte[] inputSecret = Hashes.scrypt256(inputBytes, salt);

        final BigInteger point = new BigInteger(1, inputSecret).mod(ECKey.CURVE.getN());

        final ECKey ecKey = ECKey.fromPrivate(point);

        return new ChallengePrivateKey(ecKey, salt, version);
    }

    /**
     * Decrypt an asymmetrically encrypted private key.
     *
     * <p>Notice that the resulting private key will be a root master private key, since the
     * derivation path information isn't included in the serialization. This shouldn't be a problem,
     * but is clarified since it might make the key serialization before encryption deffer from the
     * one after decryption.
     */
    @VisibleForTesting
    public PrivateKey decryptPrivateKey(
            String cypherText,
            NetworkParameters networkParameters
    ) {

        final MuunEncryptedPrivateKey encryptedKey = MuunEncryptedPrivateKey.fromBase58(
                cypherText
        );

        Preconditions.checkArgument(Arrays.equals(encryptedKey.getRecoveryCodeSalt(), salt));

        // use the least significant bytes of the ephemeral public key as deterministic IV
        final byte[] iv = Cryptography.extractDeterministicIvFromPublicKeyBytes(
                encryptedKey.getEphemeralPublicKey()
        );

        final PublicKey ephemeralPublicKey = Encodings.bytesToEcPublicKey(
                encryptedKey.getEphemeralPublicKey()
        );

        // use ECDH to compute a shared secret
        final SecretKey sharedSecret = Cryptography.computeSharedSecret(
                ephemeralPublicKey,
                getPrivateKey()
        );

        // decrypt the plaintext with AES
        final byte[] plainText = Cryptography.aesCbcNoPadding(
                encryptedKey.getCypherText(),
                iv,
                sharedSecret,
                false
        );

        return PrivateKey.fromCompactSerialization(plainText, networkParameters);
    }
}
