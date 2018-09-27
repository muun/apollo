package io.muun.common.crypto;

import io.muun.common.utils.RandomGenerator;

import org.spongycastle.crypto.BufferedBlockCipher;
import org.spongycastle.crypto.InvalidCipherTextException;
import org.spongycastle.crypto.engines.AESFastEngine;
import org.spongycastle.crypto.modes.CBCBlockCipher;
import org.spongycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.spongycastle.crypto.params.KeyParameter;
import org.spongycastle.crypto.params.ParametersWithIV;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.validation.constraints.NotNull;

/**
 * Cryptography utils.
 */
public final class Cryptography {

    // AES 256 configuration:
    public static final int AES_BLOCK_SIZE = 16;

    // RSA configuration:
    private static final String RSA_MODE = "RSA/ECB/PKCS1Padding";

    // AES (from security) configuration:
    public static final String AES_CBC_PKCS7_PADDING = "AES/CBC/PKCS7Padding";
    public static final String AES_CBC_NO_PADDING = "AES/CBC/NoPadding";

    private Cryptography() {
        throw new AssertionError();
    }

    /**
     * Clear an array with sensitive data.
     *
     * @param sensitiveData array with sensitive data.
     */
    public static void clearArray(@NotNull byte[] sensitiveData) {

        Arrays.fill(sensitiveData, (byte) 0);
    }

    /**
     * Clear an array with sensitive data.
     *
     * @param sensitiveData array with sensitive data.
     */
    public static void clearArray(@NotNull char[] sensitiveData) {

        Arrays.fill(sensitiveData, (char) 0);
    }

    /**
     * Password based encryption using AES - CBC 256 bits.
     *
     * @param input An array of bytes to be encrypted.
     * @param key   The AES key to use for encryption.
     * @return The encrypted bytes, including the random initialization vector used.
     */
    @NotNull
    public static byte[] aes256Encrypt(@NotNull byte[] input, @NotNull byte[] key) {

        // generate a random initialization vector
        final byte[] iv = RandomGenerator.getBytes(AES_BLOCK_SIZE);

        // encrypt using AES
        final BufferedBlockCipher cipher = getAesCipher(true, key, iv);
        final byte[] cipherText = new byte[cipher.getOutputSize(input.length)];

        try {
            final int length1 = cipher.processBytes(input, 0, input.length, cipherText, 0);
            final int length2 = cipher.doFinal(cipherText, length1);

            final byte[] result = new byte[iv.length + length1 + length2];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(cipherText, 0, result, iv.length, length1 + length2);

            return result;

        } catch (InvalidCipherTextException e) {
            throw new CryptographyException("Could not encrypt bytes", e);
        }
    }

    /**
     * Decrypt bytes previously encrypted with AES.
     *
     * @param input An array of bytes to be decrypted, including the initialization vector.
     * @param key   The AES key to use for decryption.
     * @return The decrypted bytes.
     */
    @NotNull
    public static byte[] aes256Decrypt(@NotNull byte[] input, @NotNull byte[] key) {

        final byte[] iv = new byte[AES_BLOCK_SIZE];
        System.arraycopy(input, 0, iv, 0, iv.length);

        final byte[] cipherText = new byte[input.length - iv.length];
        System.arraycopy(input, iv.length, cipherText, 0, cipherText.length);

        // decrypt using AES
        final BufferedBlockCipher cipher = getAesCipher(false, key, iv);
        final byte[] plainText = new byte[cipher.getOutputSize(cipherText.length)];

        try {
            final int length1 = cipher.processBytes(cipherText, 0, cipherText.length, plainText, 0);
            final int length2 = cipher.doFinal(plainText, length1);

            return Arrays.copyOf(plainText, length1 + length2);

        } catch (InvalidCipherTextException e) {
            // should not happen
            throw new CryptographyException("Could not decrypt bytes", e);

        } finally {
            // clear decrypted bytes copy
            clearArray(plainText);
        }
    }

    /**
     * Return a new initialized AES BufferedBlockCipher instance.
     */
    private static BufferedBlockCipher getAesCipher(boolean forEncryption, byte[] key, byte[] iv) {

        final BufferedBlockCipher cipher =
                new PaddedBufferedBlockCipher(new CBCBlockCipher(new AESFastEngine()));

        cipher.init(forEncryption, new ParametersWithIV(new KeyParameter(key), iv));
        return cipher;
    }

    /**
     * Encrypts a given input with RSA using a keystore friendly way.
     */
    public static byte[] rsaEncrypt(byte[] inputData, KeyStore.PrivateKeyEntry privateKeyEntry)
            throws
            NoSuchAlgorithmException,
            NoSuchPaddingException,
            InvalidKeyException,
            IOException {
        final Cipher cipher = Cipher.getInstance(RSA_MODE);
        cipher.init(Cipher.ENCRYPT_MODE, privateKeyEntry.getCertificate().getPublicKey());

        return cipherToByteArray(inputData, cipher);
    }

    /**
     * Decrypts a previously encrypted bytes with RSA using a keystore friendly way.
     */
    public static byte[] rsaDecrypt(byte[] input, KeyStore.PrivateKeyEntry privateKeyEntry) throws
            NoSuchAlgorithmException,
            NoSuchPaddingException,
            InvalidKeyException,
            IOException {
        final Cipher cipher = Cipher.getInstance(RSA_MODE);
        cipher.init(Cipher.DECRYPT_MODE, privateKeyEntry.getPrivateKey());

        final ArrayList<Byte> values = cipherToList(input, cipher);

        return listToArray(values);
    }

    /**
     * Encrypts a given input with AES using a keystore friendly way.
     */
    public static byte[] aesEncrypt(byte[] input, byte[] iv, SecretKey key) throws
            NoSuchAlgorithmException,
            NoSuchPaddingException,
            InvalidKeyException,
            InvalidAlgorithmParameterException,
            IllegalBlockSizeException,
            BadPaddingException {

        return aesEncrypt(input, iv, key, AES_CBC_PKCS7_PADDING);
    }

    /**
     * Encrypts a given input with AES using a keystore friendly way.
     */
    public static byte[] aesEncrypt(byte[] input, byte[] iv, SecretKey key, String mode) throws
            NoSuchAlgorithmException,
            NoSuchPaddingException,
            InvalidKeyException,
            InvalidAlgorithmParameterException,
            IllegalBlockSizeException,
            BadPaddingException {

        final Cipher cipher = Cipher.getInstance(mode);

        cipher.init(
                Cipher.ENCRYPT_MODE,
                key,
                new IvParameterSpec(iv),
                RandomGenerator.getSecureRandom()
        );

        return cipher.doFinal(input);
    }

    /**
     * Decrypts a previously encrypted bytes with AES using a keystore friendly way.
     */
    public static byte[] aesDecrypt(byte[] input, SecretKey key, byte[] iv)
            throws
            NoSuchAlgorithmException,
            NoSuchPaddingException,
            InvalidKeyException,
            InvalidAlgorithmParameterException,
            IllegalBlockSizeException,
            BadPaddingException {
        return aesDecrypt(input, key, iv, AES_CBC_PKCS7_PADDING);
    }

    /**
     * Decrypts a previously encrypted bytes with AES using a keystore friendly way.
     */
    public static byte[] aesDecrypt(byte[] input, SecretKey key, byte[] iv, String mode)
            throws
            NoSuchAlgorithmException,
            NoSuchPaddingException,
            InvalidKeyException,
            InvalidAlgorithmParameterException,
            IllegalBlockSizeException,
            BadPaddingException {
        final Cipher cipher = Cipher.getInstance(mode);
        cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
        return cipher.doFinal(input);
    }

    private static byte[] cipherToByteArray(byte[] inputData, Cipher cipher) throws IOException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final CipherOutputStream cipherOutputStream = new CipherOutputStream(
                outputStream, cipher);
        cipherOutputStream.write(inputData);
        cipherOutputStream.close();

        return outputStream.toByteArray();
    }

    private static byte[] listToArray(ArrayList<Byte> values) {
        final byte[] bytes = new byte[values.size()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = values.get(i);
        }

        return bytes;
    }

    @NotNull private static ArrayList<Byte> cipherToList(byte[] input, Cipher cipher) throws
            IOException {
        final ByteArrayInputStream bais = new ByteArrayInputStream(input);
        final CipherInputStream cipherInputStream = new CipherInputStream(bais, cipher);
        final ArrayList<Byte> values = new ArrayList<>();
        int nextByte;
        while ((nextByte = cipherInputStream.read()) != -1) {
            values.add((byte) nextByte);
        }
        return values;
    }
}