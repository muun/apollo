package io.muun.apollo.data.os.secure_storage;

import io.muun.common.crypto.Cryptography;

import android.os.Build;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.ProviderException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

/**
 * This class purpose is trying to work around a known issue in Android 10 regarding the Keystore.
 * See: https://issuetracker.google.com/issues/147384380.
 * Also: why isn't this in Kotlin? To keep checked exceptions in some of the methods signatures.
 */
class CryptographyWrapper {

    public static byte[] aesCbcPkcs7PaddingUsingProviders(byte[] input,
                                                          byte[] iv,
                                                          SecretKey key,
                                                          boolean forEncryption) {
        try {
            return Cryptography.aesCbcPkcs7PaddingUsingProviders(input, iv, key, forEncryption);
        } catch (ProviderException error) {

            // As class docs states, for Android 10 we retry after a small delay, else we re-throw
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                smallDelay();
                return Cryptography.aesCbcPkcs7PaddingUsingProviders(input, iv, key, forEncryption);
            } else {
                throw error;
            }
        }
    }

    public static byte[] rsaEncrypt(byte[] inputData, KeyStore.PrivateKeyEntry privateKeyEntry)
            throws InvalidKeyException,
            NoSuchAlgorithmException,
            NoSuchPaddingException,
            IOException {

        try {
            return Cryptography.rsaEncrypt(inputData, privateKeyEntry);
        } catch (ProviderException error) {

            // As class docs states, for Android 10 we retry after a small delay, else we re-throw
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                smallDelay();
                return Cryptography.rsaEncrypt(inputData, privateKeyEntry);
            } else {
                throw error;
            }
        }
    }

    public static byte[] rsaDecrypt(byte[] input, KeyStore.PrivateKeyEntry privateKeyEntry) throws
            InvalidKeyException,
            NoSuchAlgorithmException,
            NoSuchPaddingException,
            IOException {

        try {
            return Cryptography.rsaDecrypt(input, privateKeyEntry);
        } catch (ProviderException error) {

            // As class docs states, for Android 10 we retry after a small delay, else we re-throw
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                smallDelay();
                return Cryptography.rsaDecrypt(input, privateKeyEntry);
            } else {
                throw error;
            }
        }
    }

    private static void smallDelay() {
        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {
            // Do nothing.
        }
    }
}
