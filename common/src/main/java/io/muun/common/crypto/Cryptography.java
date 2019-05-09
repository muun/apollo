package io.muun.common.crypto;

import io.muun.common.utils.RandomGenerator;

import org.spongycastle.crypto.BufferedBlockCipher;
import org.spongycastle.crypto.InvalidCipherTextException;
import org.spongycastle.crypto.engines.AESFastEngine;
import org.spongycastle.crypto.engines.AESLightEngine;
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
import java.security.spec.KeySpec;
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

    // AES 128 configuration:
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
     * Encrypts a given input with AES/CBC/PKCS7Padding using a keystore friendly way.
     */
    public static byte[] aesCbcPkcs7Encrypt(byte[] input, byte[] iv, SecretKey key)
            throws InvalidCipherTextException {

        return aesTransformation(input, iv, key, true, true);
    }

    /**
     * Encrypts a given input with AES/CBC/NoPadding using a keystore friendly way.
     */
    public static byte[] aesCbcNoPaddingEncrypt(byte[] input, byte[] iv, SecretKey key)
            throws InvalidCipherTextException {

        return aesTransformation(input, iv, key, false, true);
    }

    /**
     * Decrypts a previously encrypted bytes with AES/CBC/PKCS7Padding using a keystore friendly
     * way.
     */
    public static byte[] aesCbcPkcs7Decrypt(byte[] input, SecretKey key, byte[] iv)
            throws InvalidCipherTextException {

        return aesTransformation(input, iv, key, true, false);
    }

    /**
     * Decrypts a previously encrypted bytes with AES/CBC/NoPadding using a keystore friendly way.
     *
     * @param input Precondition: should be a factor of 16
     */
    public static byte[] aesCbcNoPaddingDecrypt(byte[] input,
                                                SecretKey key,
                                                byte[] iv)
            throws InvalidCipherTextException {

        return aesTransformation(input, iv, key, false, false);
    }

    private static byte[] aesTransformation(byte[] input,
                                            byte[] iv,
                                            SecretKey key,
                                            boolean hasPadding,
                                            boolean forEncryption)
            throws InvalidCipherTextException {

        // Keys coming from the android keystore do not have the KeySpec type.
        // We need to differentiate them because we cannot access an AndroidSecretKey directly with
        // SpongyCastle (in other words: key.getEncoded() does not work for android secret keys).
        // Instead, we have to rely on the AndroidKeyStoreBCWorkaroundProvider present on the
        // platform.
        if (key instanceof KeySpec) {

            // We are able to manually handle the key using SpongyCastle directly
            return aesTransformationSpongycastle(input,iv,key,hasPadding,forEncryption);
        } else {

            // key is most likely and AndroidSecretKey from the keystore, we cannot manually handle
            // this case, we need to ask for the right provider using Cipher.getInstance.
            return aesTransformationUsingProviders(input,iv,key,hasPadding,forEncryption);
        }
    }

    private static byte[] aesTransformationSpongycastle(byte[] input,
                                            byte[] iv,
                                            SecretKey key,
                                            boolean hasPadding,
                                            boolean forEncryption)
            throws InvalidCipherTextException {

        // AESLightEngine is using AES128
        final CBCBlockCipher cbcBlockCipher = new CBCBlockCipher(new AESLightEngine());

        final BufferedBlockCipher cipher = hasPadding
                ? new PaddedBufferedBlockCipher(cbcBlockCipher)
                : new BufferedBlockCipher(cbcBlockCipher);

        cipher.init(forEncryption, new ParametersWithIV(new KeyParameter(key.getEncoded()), iv));

        final byte[] output = new byte[cipher.getOutputSize(input.length)];

        final int length1 = cipher.processBytes(input, 0, input.length, output, 0);
        final int length2 = cipher.doFinal(output, length1);

        return Arrays.copyOf(output, length1 + length2);
    }

    private static byte[] aesTransformationUsingProviders(byte[] input,
                                                          byte[] iv,
                                                          SecretKey key,
                                                          boolean hasPadding,
                                                          boolean forEncryption) {
        try {
            final Cipher cipher = Cipher.getInstance(
                    hasPadding ? AES_CBC_PKCS7_PADDING : AES_CBC_NO_PADDING);

            cipher.init(
                    forEncryption ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE,
                    key,
                    new IvParameterSpec(iv)
            );

            return cipher.doFinal(input);

        } catch (IllegalBlockSizeException
                | BadPaddingException
                | InvalidKeyException
                | InvalidAlgorithmParameterException
                | NoSuchPaddingException
                | NoSuchAlgorithmException e) {

            throw new CryptographyException(e);
        }
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

    @NotNull
    private static ArrayList<Byte> cipherToList(byte[] input, Cipher cipher) throws
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