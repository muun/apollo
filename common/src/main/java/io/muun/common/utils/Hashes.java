package io.muun.common.utils;

import com.lambdaworks.crypto.SCrypt;
import org.bouncycastle.crypto.digests.RIPEMD160Digest;
import org.bouncycastle.crypto.digests.SHA512Digest;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.KeyParameter;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.annotation.Nonnegative;
import javax.validation.constraints.NotNull;

public final class Hashes {

    private static final int SCRYPT_ITERATIONS = 512;
    private static final int SCRYPT_BLOCK_SIZE = 8;
    private static final int SCRYPT_PARALLELIZATION_FACTOR = 1;
    private static final int SCRYPT_OUTPUT_LENGTH_IN_BYTES = 32;

    private Hashes() {
        throw new AssertionError();
    }

    /**
     * Compute the HMAC-SHA512 authentication code of the given message.
     *
     * @param key The secret key.
     * @param message The array containing the bytes to authenticate.
     * @return the keyed-hash message authentication code.
     */
    @NotNull
    public static byte[] hmacSha512(@NotNull byte[] message, @NotNull byte[] key) {

        return hmacSha512(message, key, 0, message.length);
    }

    /**
     * Compute the HMAC-SHA512 authentication code of the given message.
     *
     * @param key The secret key.
     * @param message The array containing the bytes to authenticate.
     * @param offset The offset within the array of the bytes to authenticate.
     * @param length The number of bytes to authenticate.
     * @return the keyed-hash message authentication code.
     */
    @NotNull
    public static byte[] hmacSha512(@NotNull byte[] message, @NotNull byte[] key,
                                    @Nonnegative int offset, @Nonnegative int length) {

        final byte[] bytes = new byte[64];

        final HMac hmac = new HMac(new SHA512Digest());

        hmac.init(new KeyParameter(key));
        hmac.reset();
        hmac.update(message, offset, length);
        hmac.doFinal(bytes, 0);

        return bytes;
    }

    /**
     * Compute the SHA-256 hash of the given data.
     *
     * @param input The array containing the bytes to hash.
     * @return the hash (in big-endian order).
     */
    @NotNull
    public static byte[] sha256(@NotNull byte[] input) {

        return sha256(input, 0, input.length);
    }

    /**
     * Compute the SHA-256 hash of the given data.
     *
     * @param input The array containing the bytes to hash.
     * @param offset The offset within the array of the bytes to hash.
     * @param length The number of bytes to hash.
     * @return the hash (in big-endian order).
     */
    @NotNull
    public static byte[] sha256(@NotNull byte[] input, @Nonnegative int offset,
                                @Nonnegative int length) {

        final MessageDigest digest = newSha256Digest();
        digest.update(input, offset, length);

        return digest.digest();
    }

    /**
     * Compute the SHA-256 hash of the given data, and then hash the resulting hash again.
     *
     * @param input The array containing the bytes to hash.
     * @return the double hash (in big-endian order).
     */
    @NotNull
    public static byte[] sha256Twice(@NotNull byte[] input) {

        return sha256Twice(input, 0, input.length);
    }

    /**
     * Compute the SHA-256 hash of the given data, and then hash the resulting hash again.
     *
     * @param input The array containing the bytes to hash.
     * @param offset The offset within the array of the bytes to hash.
     * @param length The number of bytes to hash.
     * @return the double hash (in big-endian order).
     */
    @NotNull
    public static byte[] sha256Twice(@NotNull byte[] input, @Nonnegative int offset,
                                     @Nonnegative int length) {

        final MessageDigest digest = newSha256Digest();

        digest.update(input, offset, length);

        return digest.digest(digest.digest());
    }

    /**
     * Compute the RIPEMD-160 hash of the given data.
     *
     * @param input The array containing the bytes to hash.
     * @return the hash (in big-endian order).
     */
    @NotNull
    public static byte[] ripemd160(@NotNull byte[] input) {

        return ripemd160(input, 0, input.length);
    }

    /**
     * Compute the RIPEMD-160 hash of the given data.
     *
     * @param input The array containing the bytes to hash.
     * @param offset The offset within the array of the bytes to hash.
     * @param length The number of bytes to hash.
     * @return the hash (in big-endian order).
     */
    @NotNull
    public static byte[] ripemd160(@NotNull byte[] input, @Nonnegative int offset,
                                   @Nonnegative int length) {

        final byte[] bytes = new byte[20];

        final RIPEMD160Digest digest = new RIPEMD160Digest();

        digest.update(input, offset, length);
        digest.doFinal(bytes, 0);

        return bytes;
    }

    /**
     * Compute RIPEMD-160(SHA-256(input)).
     *
     * @param input The array containing the bytes to hash.
     * @return the hash (in big-endian order).
     */
    @NotNull
    public static byte[] sha256Ripemd160(@NotNull byte[] input) {

        return sha256Ripemd160(input, 0, input.length);
    }

    /**
     * Compute RIPEMD-160(SHA-256(input)).
     *
     * @param input The array containing the bytes to hash.
     * @param offset The offset within the array of the bytes to hash.
     * @param length The number of bytes to hash.
     * @return the hash (in big-endian order).
     */
    @NotNull
    public static byte[] sha256Ripemd160(@NotNull byte[] input, @Nonnegative int offset,
                                         @Nonnegative int length) {

        final byte[] sha256 = sha256(input, offset, length);

        return ripemd160(sha256);
    }

    /**
     * Generate an 256-bit key from a password, using Scrypt.
     *
     * @param input The password to use in key generation.
     * @param salt The salt to use.
     * @return The derived key.
     */
    @NotNull
    public static byte[] scrypt256(byte[] input, byte[] salt) {
        try {
            return SCrypt.scrypt(
                    input,
                    salt,
                    SCRYPT_ITERATIONS,
                    SCRYPT_BLOCK_SIZE,
                    SCRYPT_PARALLELIZATION_FACTOR,
                    SCRYPT_OUTPUT_LENGTH_IN_BYTES
            );
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);  // should not happen
        }
    }

    /**
     * Return a new SHA-256 MessageDigest instance.
     */
    private static MessageDigest newSha256Digest() {

        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);  // cannot happen
        }
    }
}
