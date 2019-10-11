package io.muun.common.crypto;

import io.muun.common.crypto.hd.PrivateKey;
import io.muun.common.utils.Encodings;
import io.muun.common.utils.Hashes;
import io.muun.common.utils.Preconditions;
import io.muun.common.utils.RandomGenerator;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.SignatureDecodeException;
import org.bouncycastle.jcajce.provider.asymmetric.ec.KeyPairGeneratorSpi;
import org.bouncycastle.jce.interfaces.ECPublicKey;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;

import javax.annotation.Nullable;
import javax.crypto.SecretKey;

public class ChallengePublicKey {

    public static final int PUBLIC_KEY_LENGTH = MuunEncryptedPrivateKey.PUBLIC_KEY_SIZE;

    private static final String CURVE_NAME = "secp256k1";

    private final byte[] key;

    @Nullable // nullable until migration is complete
    private final byte[] salt;

    /**
     * Constructor.
     */
    public ChallengePublicKey(byte[] publicKey, @Nullable byte[] salt) {

        // check that the public key is a valid point on the secp256k1 curve
        ECKey.CURVE.getCurve().decodePoint(publicKey);

        this.key = publicKey;
        this.salt = salt;
    }

    /**
     * Verify a signed data with the public key.
     */
    public boolean verify(byte[] data, byte[] signature) {
        final byte[] hash = Hashes.sha256(data);

        try {
            return ECKey.fromPublicOnly(key).verify(hash, signature);
        } catch (SignatureDecodeException e) {
            // TODO handle better?
            return false;
        }
    }

    /**
     * Serialize public key.
     */
    public byte[] toBytes() {
        return key;
    }

    @Nullable
    public byte[] getSalt() {
        return salt;
    }

    /**
     * Encrypt a private key asymmetrically, so that it can be decrypted with the corresponding
     * challenge private key.
     *
     * <p>IMPORTANT: to use this method, the salt of this public key must be NON-NULL.
     *
     * <p>Notice that the network parameters won't be included in the serialization, so they must be
     * provided when decrypting.
     *
     * @param privateKey the extended private key that will be encrypted.
     * @param walletBirthday the number of days since the timestamp in Bitcoin’s genesis block.
     */
    public String encryptPrivateKey(PrivateKey privateKey, long walletBirthday) {

        final KeyPair ephemeralKeypair = createEphemeralKeypair();

        final byte[] ephemeralPubkeyBytes = Encodings.ecPublicKeyToBytes(
                (ECPublicKey) ephemeralKeypair.getPublic()
        );

        // use the least significant bytes of the ephemeral public key as deterministic IV
        final byte[] iv = Cryptography.extractDeterministicIvFromPublicKeyBytes(
                ephemeralPubkeyBytes
        );

        // use ECDH to compute a shared secret
        final SecretKey sharedSecret = Cryptography.computeSharedSecret(
                Encodings.bytesToEcPublicKey(toBytes()),
                ephemeralKeypair.getPrivate()
        );

        // encrypt the plaintext with AES
        final byte[] cypherText = Cryptography.aesCbcNoPadding(
                privateKey.toCompactSerialization(),
                iv,
                sharedSecret,
                true
        );

        final MuunEncryptedPrivateKey encryptedPrivateKey = new MuunEncryptedPrivateKey(
                MuunEncryptedPrivateKey.CURRENT_VERSION,
                walletBirthday,
                ephemeralPubkeyBytes,
                cypherText,
                Preconditions.checkNotNull(salt)
        );

        return encryptedPrivateKey.toBase58();
    }

    /**
     * Create a random ephemeral key pair in the bitcoin curve.
     */
    private KeyPair createEphemeralKeypair() {

        final KeyPairGenerator kpg;

        try {
            // Using SpongyCastle's class directly, without java.security/javax.crypto security
            // providers system, to avoid issues with proguard discarding them.
            kpg = new KeyPairGeneratorSpi.ECDH();

            kpg.initialize(new ECGenParameterSpec(CURVE_NAME), RandomGenerator.getSecureRandom());

        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }

        return kpg.generateKeyPair();
    }
}
