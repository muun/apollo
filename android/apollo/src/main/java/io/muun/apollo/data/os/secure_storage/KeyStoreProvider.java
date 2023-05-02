package io.muun.apollo.data.os.secure_storage;

import io.muun.common.crypto.CryptographyException;
import io.muun.common.utils.Preconditions;

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
import java.util.HashSet;
import java.util.Set;
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

    // Note: this constant is used for J_MODE (api 19 to 22) only, though it may generate
    // limitations for M_MODE (api 23 and up) too.
    // The RSA algorithm can only encrypt data that has a maximum byte length of the RSA key length
    // in bits divided with eight minus eleven padding bytes, i.e.
    // number of maximum bytes = key length in bits / 8 (-11 if you have padding).
    // Default RSA key size is 2048, which gives us a max data storage size of 256 bytes. Since we
    // need to store more than that in some places (e.g SignupDraftManager) we are doubling it to
    // 4096 which allow us to store up to 512 (which should be more than enough).
    private static final int RSA_KEY_SIZE_IN_BITS = 4096;
    private static final int RSA_MAX_STORAGE_SIZE_IN_BYTES = RSA_KEY_SIZE_IN_BITS / 8;

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
     * Encrypt a value using an unique keystore-backed key.
     *
     * @param input Data to encrypt.
     * @param alias Key alias under which a key will be generated in the keystore.
     * @param iv    Initialization vector which will prevent an attacker to easily figure out the
     *              type of stored data, should be random or pseudo random.
     * @return encrypted data.
     */
    public byte[] encryptData(byte[] input, String alias, byte[] iv) {

        // This constraint should really apply to J_MODE only, but we enforce it across all versions
        // to keep consistency and to avoid errors or mistakes going unnoticed.
        Preconditions.checkArgument(input.length <= RSA_MAX_STORAGE_SIZE_IN_BYTES);

        try {
            final KeyStore keyStore = loadKeystore();
            final String keyAlias = getAlias(alias);

            // Note that hasKey is a public method and applies getAlias to the argument
            // so take care to supply the alias parameter instead of keyAlias.
            if (!hasKey(alias)) {
                // This is a racy operation and might end up creating 2 keys overwriting each other.
                // That might mean we store the encrypted value using key 1 but keystore
                // has key 2 stored. This should be taken care of by external locking at
                // SecureStorageProvider.

                // Android Keystore has a bug when asked to re-generate a key for the alias
                // (e.g an alias for which generateKeyStore has already been called) in a "short
                // period of time". It's bizarre and it somehow ends up with neither of the
                // generated keys being kept/stored (e.g they keystore ends up with no recollection
                // of a key for the specified alias).
                // Now, supposedly, we already guarantee "non concurrent access" to this provider
                // with our custom locking in SecureStorageProvider. The thing is that apparently
                // our "non concurrent" definition is clearly not the same than the Keystore's, at
                // least for this case in point (creation of Keystore keys).
                // So, its important that we avoid re-generating a Keystore Key for an alias for
                // which a has already been generated.
                // TODO: should we pre-generate keystore keys for all the known aliases at startup?

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
     * Decrypt a value using it's unique key backed by the keystore.
     *
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

        } catch (NoSuchAlgorithmException
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

            return CryptographyWrapper.aesCbcPkcs7PaddingUsingProviders(input, iv, key, true);

        } catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException e) {

            Timber.e(e);
            throw new MuunKeyStoreException(e);
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private byte[] decryptDataM(byte[] input, String keyAlias, KeyStore keyStore, byte[] iv) {
        try {
            final SecretKey key = (SecretKey) keyStore.getKey(keyAlias, null);

            return CryptographyWrapper.aesCbcPkcs7PaddingUsingProviders(input, iv, key, false);

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
                    .setKeySize(RSA_KEY_SIZE_IN_BITS)
                    .setEndDate(end)
                    .build();

            final KeyPairGenerator generator = KeyPairGenerator.getInstance(RSA, ANDROID_KEY_STORE);

            generator.initialize(spec);
            generator.generateKeyPair();

        } catch (NoSuchAlgorithmException
                | NoSuchProviderException
                | InvalidAlgorithmParameterException e) {

            Timber.e(e);
            throw new MuunKeyStoreException(e);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private byte[] encryptDataJ(byte[] inputData, String keyAlias, KeyStore keyStore) {
        try {
            final PrivateKeyEntry entry = (PrivateKeyEntry) keyStore.getEntry(keyAlias, null);

            return CryptographyWrapper.rsaEncrypt(inputData, entry);

        } catch (KeyStoreException
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
        try {
            final PrivateKeyEntry entry = (PrivateKeyEntry) keyStore.getEntry(keyAlias, null);

            return CryptographyWrapper.rsaDecrypt(input, entry);

        } catch (NoSuchAlgorithmException
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
     * Check whether an alias is present in the keystore.
     *
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

    Set<String> getAllLabels() throws MuunKeyStoreException {
        try {
            return new HashSet<>(Collections.list(loadKeystore().aliases()));
        } catch (KeyStoreException e) {
            throw new MuunKeyStoreException(e);
        }
    }

    public static class MuunKeyStoreException extends CryptographyException {

        public MuunKeyStoreException(Throwable cause) {
            super(cause);
        }
    }
}
