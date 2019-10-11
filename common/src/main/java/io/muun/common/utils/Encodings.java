package io.muun.common.utils;

import io.muun.common.crypto.CryptographyException;
import io.muun.common.utils.internal.Base58;

import org.bitcoinj.core.ECKey;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.util.SubjectPublicKeyInfoFactory;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;
import org.bouncycastle.jcajce.provider.asymmetric.ec.KeyFactorySpi;
import org.bouncycastle.jce.interfaces.ECPublicKey;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.validation.constraints.NotNull;

/**
 * Encoding utils.
 */
@SuppressWarnings("WeakerAccess")
public final class Encodings {

    private static final int CHECKSUM_LENGTH = 4;

    private Encodings() {
        throw new AssertionError();
    }

    /**
     * Serialize a big integer to a fixed-length byte array.
     *
     * <p>The regular {@link java.math.BigInteger#toByteArray()} method isn't quite what we often
     * need: it appends a leading zero to indicate that the number is positive and may need padding.
     *
     * @param number The big integer to serialize.
     * @param numBytes The desired size of the resulting byte array.
     * @return {@code numBytes} byte long 0-padded big-endian array.
     */
    @Nonnull
    public static byte[] bigIntegerToBytes(@Nonnull BigInteger number, @Nonnegative int numBytes) {

        final byte[] bytes = number.toByteArray();
        final byte[] fixedBytes = new byte[numBytes];

        final int length = Math.min(bytes.length, numBytes);

        System.arraycopy(bytes, bytes.length - length, fixedBytes, numBytes - length, length);

        clearArray(bytes);

        return fixedBytes;
    }

    /**
     * Deserialize a big integer from a big-endian byte array.
     *
     * @param bytes The big-endian byte array to deserialize.
     * @return the deserialized big integer.
     */
    @Nonnull
    public static BigInteger bytesToBigInteger(@Nonnull byte[] bytes) {

        return new BigInteger(1, bytes);
    }

    /**
     * Serialize a string to a UTF-8 encoded byte array.
     *
     * @param input The string to serialize.
     * @return the UTF-8 encoded byte array.
     */
    @Nonnull
    public static byte[] stringToBytes(@Nonnull String input) {

        return input.getBytes(Charset.forName("UTF-8"));
    }

    /**
     * Deserialize a UTF-8 encoded byte array to a string.
     *
     * @param bytes The UTF-8 encoded byte array.
     * @return the deserialized string.
     */
    @Nonnull
    public static String bytesToString(@Nonnull byte[] bytes) {

        return new String(bytes, Charset.forName("UTF-8"));
    }

    /**
     * Serialize a native integer.
     * NOTE: this uses an intermediate String representation, so endianness is not a concern.
     */
    @Nonnull
    public static byte[] intToBytes(int aInt) {
        return stringToBytes(Integer.toString(aInt));
    }

    /**
     * Deserialize a native integer.
     */
    public static int bytesToInt(@Nonnull byte[] bytes) {
        return Integer.parseInt(bytesToString(bytes));
    }

    /**
     * Serialize a char sequence to a UTF-8 encoded byte array.
     *
     * @param input The char sequence to serialize.
     * @return the UTF-8 encoded byte array.
     */
    @Nonnull
    public static byte[] charSequenceToBytes(@Nonnull CharSequence input) {

        final CharBuffer inputBuffer = CharBuffer.wrap(input);
        final ByteBuffer outputBuffer = Charset.forName("UTF-8").encode(inputBuffer);

        final byte[] output = new byte[outputBuffer.limit()];
        outputBuffer.get(output);

        return output;
    }

    /**
     * Append a 4-byte checksum to a byte array and serialize it to a base 58 string.
     *
     * @param bytes The byte array to serialize.
     * @return the check-summed base 58 string.
     */
    @Nonnull
    public static String bytesToCheckedBase58(@Nonnull byte[] bytes) {

        final byte[] hash = Hashes.sha256Twice(bytes);
        final byte[] checkedBytes = new byte[bytes.length + CHECKSUM_LENGTH];

        System.arraycopy(bytes, 0, checkedBytes, 0, bytes.length);
        System.arraycopy(hash, 0, checkedBytes, bytes.length, CHECKSUM_LENGTH);

        try {
            return Base58.encode(checkedBytes);
        } finally {
            clearArray(checkedBytes);
        }
    }

    /**
     * Deserialize a base 58 string and verify that the last 4 bytes are the correct checksum.
     *
     * <p>This is the inverse operation to {@link #bytesToCheckedBase58(byte[])}.
     *
     * @param input The check-summed base 58 string.
     * @return the decoded byte array, without the final checksum.
     */
    @Nonnull
    public static byte[] checkedBase58ToBytes(@Nonnull String input) {

        final byte[] checkedBytes;

        try {
            checkedBytes = Base58.decode(input);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid base 58 string", e);
        }

        if (checkedBytes.length < CHECKSUM_LENGTH) {
            throw new IllegalArgumentException("Invalid checked base 58 string: too short");
        }

        // extract data
        final byte[] bytes = new byte[checkedBytes.length - CHECKSUM_LENGTH];
        final byte[] checksum = new byte[CHECKSUM_LENGTH];

        System.arraycopy(checkedBytes, 0, bytes, 0, bytes.length);
        System.arraycopy(checkedBytes, bytes.length, checksum, 0, checksum.length);

        clearArray(checkedBytes);

        // verify checksum
        final byte[] hash = Hashes.sha256Twice(bytes);
        final byte[] expectedChecksum = new byte[CHECKSUM_LENGTH];

        System.arraycopy(hash, 0, expectedChecksum, 0, expectedChecksum.length);

        if (!Arrays.equals(checksum, expectedChecksum)) {
            throw new IllegalArgumentException("Invalid checked base 58 string: invalid checksum");
        }

        return bytes;
    }

    /**
     * Serialize a byte array to a hexadecimal string.
     *
     * @param bytes The byte array to format into a hexadecimal string.
     * @return an even-length 0-padded lower-case hexadecimal string.
     */
    @Nonnull
    public static String bytesToHex(@Nonnull byte[] bytes) {

        final char[] output = new char[bytes.length * 2];

        for (int inputPos = 0, outputPos = 0; inputPos < bytes.length; inputPos++) {

            output[outputPos++] = Character.forDigit((bytes[inputPos] & 0xf0) >>> 4, 16);
            output[outputPos++] = Character.forDigit((bytes[inputPos] & 0x0f), 16);
        }

        final String stringOutput = new String(output);

        clearArray(output);

        return stringOutput;
    }

    /**
     * Deserialize a hexadecimal string to a byte array.
     *
     * @param input The even-length 0-padded hexadecimal string.
     * @return the decoded byte array.
     */
    @Nonnull
    public static byte[] hexToBytes(@Nonnull String input) {

        if (input.length() % 2 != 0) {
            throw new IllegalArgumentException("Invalid hex string: odd length");
        }

        final byte[] bytes = new byte[input.length() / 2];

        for (int inputPos = 0, outputPos = 0; outputPos < bytes.length; outputPos++) {

            final int firstDigit = Character.digit(input.charAt(inputPos++), 16);
            final int secondDigit = Character.digit(input.charAt(inputPos++), 16);

            if (firstDigit == -1 || secondDigit == -1) {
                throw new IllegalArgumentException("Invalid hex string: illegal character");
            }

            bytes[outputPos] = (byte) ((firstDigit << 4) + secondDigit);
        }

        return bytes;
    }

    /**
     * Serialize a bouncy castle secp256k1 EC public key into its compressed ASN.1 point
     * serialization.
     */
    @Nonnull
    public static byte[] ecPublicKeyToBytes(@Nonnull ECPublicKey publicKey) {

        return publicKey.getQ().getEncoded(true);
    }

    /**
     * Deserialize a bouncy castle secp256k1 EC public key from its compressed ASN.1 point
     * serialization.
     */
    @Nonnull
    public static ECPublicKey bytesToEcPublicKey(@Nonnull byte[] bytes) {

        final ECPublicKeyParameters keyParameters = new ECPublicKeyParameters(
                ECKey.CURVE.getCurve().decodePoint(bytes),
                ECKey.CURVE
        );

        try {

            final SubjectPublicKeyInfo keyInfo = SubjectPublicKeyInfoFactory
                    .createSubjectPublicKeyInfo(keyParameters);

            return (BCECPublicKey) new KeyFactorySpi.ECDH().generatePublic(keyInfo);

        } catch (IOException e) {
            throw new CryptographyException(e);
        }
    }

    /**
     * Clear an array with sensitive data.
     *
     * @param sensitiveData array with sensitive data.
     */
    private static void clearArray(@NotNull byte[] sensitiveData) {

        Arrays.fill(sensitiveData, (byte) 0);
    }

    /**
     * Clear an array with sensitive data.
     *
     * @param sensitiveData array with sensitive data.
     */
    private static void clearArray(@NotNull char[] sensitiveData) {

        Arrays.fill(sensitiveData, (char) 0);
    }
}
