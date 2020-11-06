package io.muun.common.crypto;

import io.muun.common.utils.Preconditions;
import io.muun.common.utils.RandomGenerator;

import org.bouncycastle.crypto.BufferedBlockCipher;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.AESFastEngine;
import org.bouncycastle.crypto.engines.AESLightEngine;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.bouncycastle.jcajce.provider.asymmetric.ec.KeyAgreementSpi;
import org.bouncycastle.pqc.math.linearalgebra.ByteUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
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

    static {
        // Needed for supporting large key sizes.
        Security.setProperty("crypto.policy", "unlimited");
    }

    // ECDH configuration:
    private static final String ENCRYPTION_ALGORITHM = "AES";

    // AES 128 configuration:
    public static final int AES_BLOCK_SIZE = 16;

    // RSA configuration:
    private static final String RSA_MODE = "RSA/ECB/PKCS1Padding";

    // AES (from security) configuration:
    private static final String AES_CBC_PKCS7_PADDING = "AES/CBC/PKCS7Padding";

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
     * Encrypt a given input with RSA using a system cipher provider. This is needed to interact
     * with keys that are in the Android keystore.
     */
    public static byte[] rsaEncrypt(byte[] plaintext, KeyStore.PrivateKeyEntry privateKeyEntry)
            throws NoSuchAlgorithmException,
            NoSuchPaddingException,
            InvalidKeyException,
            IOException {

        final Cipher cipher = Cipher.getInstance(RSA_MODE);
        cipher.init(Cipher.ENCRYPT_MODE, privateKeyEntry.getCertificate().getPublicKey());

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final CipherOutputStream cipherOutputStream = new CipherOutputStream(outputStream, cipher);

        cipherOutputStream.write(plaintext);
        cipherOutputStream.close();

        return outputStream.toByteArray();
    }

    /**
     * Decrypt a given ciphertext with RSA using a system cipher provider. This is needed to
     * interact with keys that are in the Android keystore.
     */
    public static byte[] rsaDecrypt(byte[] ciphertext, KeyStore.PrivateKeyEntry privateKeyEntry)
            throws NoSuchAlgorithmException,
            NoSuchPaddingException,
            InvalidKeyException,
            IOException {

        final Cipher cipher = Cipher.getInstance(RSA_MODE);
        cipher.init(Cipher.DECRYPT_MODE, privateKeyEntry.getPrivateKey());

        final ByteArrayInputStream inputStream = new ByteArrayInputStream(ciphertext);
        final CipherInputStream cipherInputStream = new CipherInputStream(inputStream, cipher);

        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nextByte;

        while ((nextByte = cipherInputStream.read()) != -1) {
            buffer.write(nextByte);
        }

        return buffer.toByteArray();
    }

    /**
     * Encrypt or decrypt with AES/CBC/NoPadding.
     */
    public static byte[] aesCbcNoPadding(byte[] input,
                                         byte[] iv,
                                         SecretKey key,
                                         boolean forEncryption) {

        Preconditions.checkArgument(input.length % AES_BLOCK_SIZE == 0, "input should be padded");

        // AESLightEngine is using AES128
        final CBCBlockCipher cbcBlockCipher = new CBCBlockCipher(new AESLightEngine());

        final BufferedBlockCipher cipher = new BufferedBlockCipher(cbcBlockCipher);

        cipher.init(forEncryption, new ParametersWithIV(new KeyParameter(key.getEncoded()), iv));

        final byte[] output = new byte[cipher.getOutputSize(input.length)];

        try {

            final int length1 = cipher.processBytes(input, 0, input.length, output, 0);
            final int length2 = cipher.doFinal(output, length1);

            return Arrays.copyOf(output, length1 + length2);

        } catch (InvalidCipherTextException e) {
            throw new CryptographyException(e);
        }
    }

    /**
     * Encrypt or decrypt with AES/CBC/Pkcs7Padding using a system cipher provider. This is needed
     * to interact with keys that are in the Android keystore.
     */
    public static byte[] aesCbcPkcs7PaddingUsingProviders(byte[] input,
                                                          byte[] iv,
                                                          SecretKey key,
                                                          boolean forEncryption) {
        try {
            final Cipher cipher = Cipher.getInstance(AES_CBC_PKCS7_PADDING);

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

    /**
     * Compute a shared secret key using ECDH, from a remote public key and a local private key.
     */
    public static SecretKey computeSharedSecret(PublicKey remotePubKey, PrivateKey localPrivKey) {

        try {
            // Using SpongyCastle's class directly, without java.security/javax.crypto security
            // providers system, to avoid issues with proguard discarding them.
            // Plus, this is our own private class that extends SC's one to use protected methods.
            final DiffieHellman dh = new DiffieHellman();

            dh.init(localPrivKey, RandomGenerator.getSecureRandom());
            dh.doPhase(remotePubKey, true);

            return dh.generateSecret(ENCRYPTION_ALGORITHM);

        } catch (GeneralSecurityException e) {
            throw new CryptographyException(e);
        }
    }

    public static byte[] extractDeterministicIvFromPublicKeyBytes(byte[] publicKeyBytes) {
        final int length = publicKeyBytes.length;
        return ByteUtils.subArray(publicKeyBytes, length - AES_BLOCK_SIZE, length);
    }

    /**
     * AHA! If you arrive here, this you may very well think that this is another one of our dirty
     * ugly hacks... and you wouldn't be wrong! In this case, we want to directly access one of
     * SpongyCastle's precious classes, but although it is visible and can be instantiated, none
     * of its methods are public. So, we extend it and implement "wrapper" methods to the methods
     * we need.
     */
    private static class DiffieHellman extends KeyAgreementSpi.DH {

        private DiffieHellman() {
            super();
        }

        private void init(Key key, SecureRandom secureRandom) throws InvalidKeyException {
            super.engineInit(key, secureRandom);
        }

        private void doPhase(Key key, boolean lastPhase) throws InvalidKeyException {
            super.engineDoPhase(key, lastPhase);
        }

        private SecretKey generateSecret(String algorithm) throws NoSuchAlgorithmException {
            return super.engineGenerateSecret(algorithm);
        }
    }
}