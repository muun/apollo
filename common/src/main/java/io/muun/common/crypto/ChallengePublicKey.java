package io.muun.common.crypto;

import io.muun.common.crypto.hd.PrivateKey;
import io.muun.common.utils.Encodings;
import io.muun.common.utils.Hashes;
import io.muun.common.utils.Preconditions;

import com.google.common.primitives.Bytes;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.SignatureDecodeException;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.pqc.math.linearalgebra.ByteUtils;

import java.security.KeyPair;
import java.util.Arrays;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.crypto.SecretKey;

public class ChallengePublicKey {

    public static final int PUBLIC_KEY_LENGTH = MuunEncryptedPrivateKey.PUBLIC_KEY_SIZE;

    private final byte[] key;

    @Nullable // nullable until migration is complete
    private final byte[] salt;

    private final int version;

    /**
     * Legacy Constructor, used only for compat/migration code.
     */
    public static ChallengePublicKey buildLegacy(byte[] publicKey, @Nullable byte[] salt) {
        return new ChallengePublicKey(publicKey, salt, 1); // Thank god they were all at v1
    }

    /**
     * Deserialize a legacy ChallengePublicKey serialization, used only for compat/migration code.
     */
    public static ChallengePublicKey deserializeLegacy(byte[] publicKeySerialization) {
        final int length = ChallengePublicKey.PUBLIC_KEY_LENGTH;
        final byte[] publicKey = ByteUtils.subArray(publicKeySerialization, 0, length);
        final byte[] salt = ByteUtils.subArray(publicKeySerialization, length);

        // Init ChallengePublicKey version to 1, the version the keys had prior to the migration
        return new ChallengePublicKey(publicKey, salt, 1);
    }

    /**
     * Deserialize challenge public key.
     */
    @Nonnull
    public static ChallengePublicKey deserialize(byte[] serialization) {
        final int length = ChallengePublicKey.PUBLIC_KEY_LENGTH;
        final int version = serialization[0] & 0xFF; // can't use Byte.toUnsignedInt
        final byte[] publicKey = ByteUtils.subArray(serialization, 1, 1 + length);
        final byte[] salt = ByteUtils.subArray(serialization, 1 + length);

        return new ChallengePublicKey(publicKey, salt, version);
    }

    /**
     * Constructor.
     */
    public ChallengePublicKey(byte[] publicKey, @Nullable byte[] salt, int version) {

        Preconditions.checkArgument(salt == null || salt.length == 0 || salt.length == 8);

        // check that the public key is a valid point on the secp256k1 curve
        ECKey.CURVE.getCurve().decodePoint(publicKey);

        this.key = publicKey;
        this.salt = salt;
        this.version = version;
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
     * Serialize challenge public key.
     */
    public byte[] serialize() {

        final byte[] version = new byte[]{ (byte) this.version };
        final byte[][] parts = new byte[][] { version, key, salt};

        return Bytes.concat(parts);
    }

    /**
     * Get the bytes of the pub key itself.
     */
    public byte[] toBytes() {
        return key;
    }

    @Nullable
    public byte[] getSalt() {
        return salt;
    }

    public int getVersion() {
        return version;
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
    public String encryptPrivateKey(
            MuunEncryptedPrivateKey.Version serializationVersion,
            PrivateKey privateKey,
            long walletBirthday
    ) {

        final KeyPair ephemeralKeypair = Cryptography.createEphemeralKeyPair();

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

        final MuunEncryptedPrivateKey encryptedPrivateKey = MuunEncryptedPrivateKey.create(
                serializationVersion,
                walletBirthday,
                ephemeralPubkeyBytes,
                cypherText,
                Preconditions.checkNotNull(salt)
        );

        return encryptedPrivateKey.toBase58();
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
     * @param muunPrivateKey the encoded muunPrivateKey in base58.
     * @param privateKey the extended private key that will be encrypted.
     * @param walletBirthday the number of days since the timestamp in Bitcoin’s genesis block.
     */
    public String encryptPrivateKey(
            String muunPrivateKey,
            PrivateKey privateKey,
            long walletBirthday
    ) {
        final MuunEncryptedPrivateKey.Version version = MuunEncryptedPrivateKey.Version
                .fromEncryptedPrivateKey(muunPrivateKey);

        return encryptPrivateKey(version, privateKey, walletBirthday);
    }

    public String getChecksum() {
        final byte[] keySha = Hashes.sha256(key);
        final byte[] last8Bytes = Arrays.copyOfRange(keySha, keySha.length - 8, keySha.length);
        return Encodings.bytesToHex(last8Bytes);
    }
}
