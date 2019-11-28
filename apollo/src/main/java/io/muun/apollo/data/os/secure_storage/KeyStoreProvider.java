package io.muun.apollo.data.os.secure_storage;

import io.muun.common.crypto.Cryptography;
import io.muun.common.crypto.CryptographyException;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.security.KeyPairGeneratorSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import androidx.annotation.RequiresApi;
import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.temporal.ChronoUnit;
import timber.log.Timber;

import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableEntryException;
import java.security.UnrecoverableKeyException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;

import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.security.auth.x500.X500Principal;
import javax.validation.constraints.NotNull;

@Singleton
public class KeyStoreProvider {
    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";
    private static final String MUUN_KEY_STORE_PREFIX = "muun_key_store_";
    private static final X500Principal
            SUBJECT = new X500Principal("CN=io.muun.apollo, O=Android Authority");
    private static final String RSA = "RSA";

    private final Context context;

    @Inject
    public KeyStoreProvider(Context context) {
        this.context = context;
    }

    private KeyStore loadKeystore() {
        try {
            final KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
            keyStore.load(null);

            return keyStore;

        } catch (Exception e) {
            Timber.e(e);
            throw new MuunKeyStoreException(e);
        }
    }

    @NotNull
    private String getAlias(String keyAlias) {
        return MUUN_KEY_STORE_PREFIX + keyAlias;
    }

    private void generateKeyStore(String keyAlias) {
        if (SecureStorageMode.getModeForDevice() == SecureStorageMode.J_MODE) {
            generateKeyStoreJ(keyAlias);

        } else {
            generateKeyStoreM(keyAlias);
        }
    }

    /**
     * @param input Data to encrypt.
     * @param alias Key alias under which a key will be generated in the keystore.
     * @param iv    Initialization vector which will prevent an attacker to easily figure out the
     *              type of stored data, should be random or pseudo random.
     * @return encrypted data.
     */
    public byte[] encryptData(byte[] input, String alias, byte[] iv) {
        try {
            final KeyStore keyStore = loadKeystore();
            final String keyAlias = getAlias(alias);

            if (!hasKey(keyAlias)) {
                generateKeyStore(keyAlias);
            }

            return SecureStorageMode.getModeForDevice() == SecureStorageMode.J_MODE
                    ? encryptDataJ(input, keyAlias, keyStore)
                    : encryptDataM(input, keyAlias, keyStore, iv);

        } catch (Exception e) {
            Timber.e(e);
            throw new MuunKeyStoreException(e);
        }
    }

    /**
     * @param input Data to decrypt.
     * @param alias Key alias under which the data was encrypted in the first place.
     * @param iv    Initialization vector that was user to encrypt the data.
     * @return Decrypted data.
     */
    public byte[] decryptData(byte[] input, String alias, byte[] iv) {
        try {
            final KeyStore keyStore = loadKeystore();
            final String keyAlias = getAlias(alias);
            return SecureStorageMode.getModeForDevice() == SecureStorageMode.J_MODE
                    ? decryptDataJ(input, keyAlias, keyStore)
                    : decryptDataM(input, keyAlias, keyStore, iv);

        } catch (Exception e) {
            Timber.e(e);
            throw new MuunKeyStoreException(e);
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void generateKeyStoreM(String keyAlias) {
        try {
            final KeyGenerator keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEY_STORE);

            keyGenerator.init(
                    new KeyGenParameterSpec.Builder(keyAlias,
                            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                            .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                            .setRandomizedEncryptionRequired(false)
                            .build());

            keyGenerator.generateKey();

        } catch (
                NoSuchAlgorithmException
                        | NoSuchProviderException
                        | InvalidAlgorithmParameterException e) {
            Timber.e(e);
            throw new MuunKeyStoreException(e);
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private byte[] encryptDataM(byte[] input, String keyAlias, KeyStore keyStore, byte[] iv) {

        try {
            final SecretKey key = (SecretKey) keyStore.getKey(keyAlias, null);

            return Cryptography.aesCbcPkcs7PaddingUsingProviders(input, iv, key, true);

        } catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException e) {

            Timber.e(e);
            throw new MuunKeyStoreException(e);
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private byte[] decryptDataM(byte[] input, String keyAlias, KeyStore keyStore, byte[] iv) {
        try {
            final SecretKey key = (SecretKey) keyStore.getKey(keyAlias, null);

            return Cryptography.aesCbcPkcs7PaddingUsingProviders(input, iv, key, false);

        } catch (NoSuchAlgorithmException | UnrecoverableKeyException | KeyStoreException e) {

            Timber.e(e);
            throw new MuunKeyStoreException(e);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void generateKeyStoreJ(String keyAlias) {
        try {
            final Date start = new Date(
                    ZonedDateTime.now()
                            .toInstant()
                            .toEpochMilli());
            final Date end = new Date(
                    ZonedDateTime.now()
                            .plus(20, ChronoUnit.YEARS)
                            .toInstant()
                            .toEpochMilli());

            final KeyPairGeneratorSpec spec = new KeyPairGeneratorSpec.Builder(context)
                    .setAlias(keyAlias)
                    .setSubject(SUBJECT)
                    .setSerialNumber(BigInteger.ONE)
                    .setStartDate(start)
                    .setEndDate(end)
                    .build();

            final KeyPairGenerator generator = KeyPairGenerator.getInstance(RSA,
                    ANDROID_KEY_STORE);
            generator.initialize(spec);
            generator.generateKeyPair();

        } catch (
                NoSuchAlgorithmException
                        | NoSuchProviderException
                        | InvalidAlgorithmParameterException e) {
            Timber.e(e);
            throw new MuunKeyStoreException(e);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private byte[] encryptDataJ(byte[] inputData, String keyAlias, KeyStore keyStore) {
        try {
            final PrivateKeyEntry privateKeyEntry
                    = (PrivateKeyEntry) keyStore.getEntry(keyAlias, null);

            return Cryptography.rsaEncrypt(inputData, privateKeyEntry);

        } catch (
                KeyStoreException
                        | NoSuchAlgorithmException
                        | UnrecoverableEntryException
                        | InvalidKeyException
                        | NoSuchPaddingException
                        | IOException e) {
            Timber.e(e);
            throw new MuunKeyStoreException(e);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private byte[] decryptDataJ(byte[] input, String keyAlias, KeyStore keyStore) {
        PrivateKeyEntry privateKeyEntry = null;
        try {
            privateKeyEntry = (PrivateKeyEntry) keyStore.getEntry(keyAlias,
                    null);

            return Cryptography.rsaDecrypt(input, privateKeyEntry);

        } catch (
                NoSuchAlgorithmException
                        | NoSuchPaddingException
                        | UnrecoverableEntryException
                        | KeyStoreException
                        | InvalidKeyException
                        | IOException e) {
            Timber.e(e);
            throw new MuunKeyStoreException(e);
        }
    }

    /**
     * @param keyAlias Key alias that was used to encrypt data.
     * @return True if that key was generated.
     */
    public boolean hasKey(String keyAlias) {
        try {
            return loadKeystore().containsAlias(getAlias(keyAlias));

        } catch (KeyStoreException e) {
            Timber.e(e);
            throw new MuunKeyStoreException(e);
        }
    }

    /**
     * Delete a previously generated key, this is not reversible, all data which that key encrypted
     * is not recoverable.
     *
     * @param keyAlias Key alias that was used to encrypt data.
     */
    public void deleteEntry(String keyAlias) {
        try {
            loadKeystore().deleteEntry(getAlias(keyAlias));

        } catch (KeyStoreException e) {
            Timber.e(e);
            throw new MuunKeyStoreException(e);
        }
    }

    /**
     * Wipe all keys generated by this keystore, other keys are not affected.
     */
    public void wipe() {

        final ArrayList<String> allKeysAliases;
        final KeyStore keyStore;

        try {
            keyStore = loadKeystore();
            allKeysAliases = Collections.list(keyStore.aliases());
        } catch (KeyStoreException e) {
            Timber.e(e);
            throw new MuunKeyStoreException(e);
        }

        for (String alias : allKeysAliases) {
            if (alias.startsWith(MUUN_KEY_STORE_PREFIX)) {
                try {
                    keyStore.deleteEntry(alias);
                } catch (KeyStoreException e) {
                    Timber.e(e);
                }
            }
        }
    }

    public static class MuunKeyStoreException extends CryptographyException {

        public MuunKeyStoreException(Throwable cause) {
            super(cause);
        }
    }
}
