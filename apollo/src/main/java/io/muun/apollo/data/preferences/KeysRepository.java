package io.muun.apollo.data.preferences;

import io.muun.apollo.data.logging.Logger;
import io.muun.apollo.data.os.secure_storage.SecureStorageProvider;
import io.muun.apollo.data.preferences.adapter.PublicKeyPreferenceAdapter;
import io.muun.apollo.domain.errors.MissingMigrationError;
import io.muun.common.crypto.ChallengePublicKey;
import io.muun.common.crypto.ChallengeType;
import io.muun.common.crypto.hd.PrivateKey;
import io.muun.common.crypto.hd.PublicKey;
import io.muun.common.crypto.hd.PublicKeyPair;
import io.muun.common.rx.ObservableFn;
import io.muun.common.utils.Preconditions;

import android.content.Context;
import com.f2prateek.rx.preferences.Preference;
import org.bitcoinj.core.NetworkParameters;
import org.bouncycastle.pqc.math.linearalgebra.ByteUtils;
import org.threeten.bp.ZonedDateTime;
import rx.Observable;
import timber.log.Timber;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.validation.constraints.NotNull;

public class KeysRepository extends BaseRepository {

    private static final int SECONDS_IN_A_DAY = 60 * 60 * 24;

    // Storage-level keys:
    private static final String KEY_BASE_PRIVATE_KEY_PATH = "xpriv_path";
    private static final String KEY_BASE_58_PRIVATE_KEY = "key_base_58_private_key";
    private static final String KEY_ENCRYPTED_PRIVATE_MUUN_KEY = "key_encrypted_private_muun_key";
    private static final String KEY_CHALLENGE_PUBLIC_KEY = "key_challenge_public_key_";
    private static final String KEY_ENCRYPTED_PRIVATE_APOLLO_KEY =
            "key_encrypted_private_apollo_key";
    private static final String KEY_BASE_PUBLIC_KEY = "base_public_key";
    private static final String KEY_BASE_MUUN_PUBLIC_KEY = "base_muun_public_key";

    private static final String KEY_MAX_USED_EXTERNAL_ADDRESS_INDEX =
            "key_max_used_external_address_index";

    private static final String KEY_MAX_WATCHING_EXTERNAL_ADDRESS_INDEX =
            "key_max_watching_external_address_index";

    // Reactive preferences:
    private final Preference<String> basePrivateKeyPathPreference;

    private final Preference<PublicKey> basePublicKeyPreference;
    private final Preference<PublicKey> baseMuunPublicKeyPreference;

    private final Preference<Integer> maxUsedExternalAddressIndexPreference;
    private final Preference<Integer> maxWatchingExternalAddressIndexPreference;

    // Dependencies:
    private final NetworkParameters networkParameters;
    private final SecureStorageProvider secureStorageProvider;
    private final UserRepository userRepository;

    /**
     * Creates a KeysRepository.
     */
    @Inject
    public KeysRepository(
            Context context,
            NetworkParameters networkParameters,
            SecureStorageProvider secureStorageProvider,
            UserRepository userRepository) {

        super(context);

        this.networkParameters = networkParameters;

        this.secureStorageProvider = secureStorageProvider;

        this.userRepository = userRepository;

        basePrivateKeyPathPreference = rxSharedPreferences
                .getString(KEY_BASE_PRIVATE_KEY_PATH);

        maxUsedExternalAddressIndexPreference = rxSharedPreferences
                .getInteger(KEY_MAX_USED_EXTERNAL_ADDRESS_INDEX);

        maxWatchingExternalAddressIndexPreference = rxSharedPreferences
                .getInteger(KEY_MAX_WATCHING_EXTERNAL_ADDRESS_INDEX);

        basePublicKeyPreference = rxSharedPreferences.getObject(
                KEY_BASE_PUBLIC_KEY,
                PublicKeyPreferenceAdapter.INSTANCE
        );

        baseMuunPublicKeyPreference = rxSharedPreferences.getObject(
                KEY_BASE_MUUN_PUBLIC_KEY,
                PublicKeyPreferenceAdapter.INSTANCE
        );
    }

    /**
     * Returns a new non-stored root PrivateKey.
     */
    @NotNull
    public PrivateKey createRootPrivateKey() {
        return PrivateKey.getNewRootPrivateKey(networkParameters);
    }

    /**
     * Returns the current base private key.
     */
    @NotNull
    public Observable<PrivateKey> getBasePrivateKey() {
        return secureStorageProvider.getAsync(KEY_BASE_58_PRIVATE_KEY)
                .map(base58Bytes -> {
                    final String path = basePrivateKeyPathPreference.get();
                    final String base58 = new String(base58Bytes);

                    return PrivateKey.deserializeFromBase58(path, base58);
                });
    }

    /**
     * Returns the base public key.
     */
    public PublicKey getBasePublicKey() {
        final PublicKey publicKey = basePublicKeyPreference.get();
        Preconditions.checkState(publicKey != null, "PublicKey must NOT be null!");

        return publicKey;
    }

    /**
     * Returns the base Muun cosigning public key.
     */
    public PublicKey getBaseMuunPublicKey() {
        final PublicKey publicKey = baseMuunPublicKeyPreference.get();
        Preconditions.checkState(publicKey != null, "Muun cosigning PublicKey must NOT be null!");

        return publicKey;
    }

    /**
     * Returns the base user-Muun public key pair.
     */
    public PublicKeyPair getBasePublicKeyPair() {
        return new PublicKeyPair(
                getBasePublicKey(),
                getBaseMuunPublicKey()
        );
    }

    /**
     * Save the base Muun cosigning public key.
     */
    public void storeBaseMuunPublicKey(@NotNull PublicKey publicKey) {
        baseMuunPublicKeyPreference.set(publicKey);
    }

    /**
     * Saves the base private key from which all are derived.
     */
    public Observable<Void> storeBasePrivateKey(@NotNull PrivateKey basePrivateKey) {
        final byte[] base58 = basePrivateKey.serializeBase58().getBytes();

        return secureStorageProvider.putAsync(KEY_BASE_58_PRIVATE_KEY, base58)
                .flatMap(ignored -> storeEncryptedBasePrivateKey(basePrivateKey))
                .doOnNext(ignored -> {
                    basePrivateKeyPathPreference.set(basePrivateKey.getAbsoluteDerivationPath());

                    basePublicKeyPreference.set(basePrivateKey.getPublicKey());
                    maxUsedExternalAddressIndexPreference.set(-1); // set a default
                });
    }

    private Observable<Void> storeEncryptedBasePrivateKey(@NotNull PrivateKey basePrivateKey) {
        // We want to keep the flow on an Observable<View> which has `doOnNext` transformations.
        // In order to do that we are always returning a result, so that onNext is called on
        // the observable. `onErrorResumeNext(Observable.just(null))` keeps the flow on errors
        // and the `return Observable.just(null)` on `flatMap` is just passing that that null.

        if (!hasChallengePublicKey(ChallengeType.RECOVERY_CODE)) {
            // Without challenge keys, we can't store the exportable encrypted private key:
            return Observable.just(null);
        }

        return getChallengePublicKey(ChallengeType.RECOVERY_CODE)
                .map(challengePublicKey -> {
                    final long birthday = getWalletBirthdaySinceGenesis();
                    return challengePublicKey.encryptPrivateKey(basePrivateKey, birthday);
                })
                .onErrorResumeNext(error -> {
                    Logger.error(error);
                    return Observable.just(null);
                })
                .flatMap(encryptedKey -> {
                    if (encryptedKey == null) {
                        return Observable.just(null);
                    }

                    Timber.d("Storing encrypted Apollo private key in secure storage.");

                    return secureStorageProvider.putAsync(
                            KEY_ENCRYPTED_PRIVATE_APOLLO_KEY,
                            encryptedKey.getBytes()
                    );
                });
    }

    private long getWalletBirthdaySinceGenesis() {

        final ZonedDateTime createdAt = userRepository.fetchOne().createdAt;
        if (createdAt == null) {
            return 0xFFFF;
        }

        final long creationTimestamp = createdAt.toEpochSecond();
        final long genesisTimestamp = networkParameters.getGenesisBlock().getTimeSeconds();
        return (creationTimestamp - genesisTimestamp) / SECONDS_IN_A_DAY;
    }

    public Observable<String> getEncryptedBasePrivateKey() {
        return secureStorageProvider.getAsync(KEY_ENCRYPTED_PRIVATE_APOLLO_KEY)
                .map(String::new);
    }

    private Observable<ChallengePublicKey> getChallengePublicKey(ChallengeType type) {

        return secureStorageProvider
                .getAsync(KEY_CHALLENGE_PUBLIC_KEY + type.toString())
                .map(publicKeySerialization -> {
                    final int length = ChallengePublicKey.PUBLIC_KEY_LENGTH;
                    final byte[] publicKey = ByteUtils.subArray(publicKeySerialization, 0, length);
                    final byte[] salt = ByteUtils.subArray(publicKeySerialization, length);

                    // NOTE:
                    // Old Apollo versions did not store the challenge key salt. Modern Apollo
                    // versions concatenate the salt to the public key bytes (see the store method
                    // below). We use the length of the stored key to determine whether the salt
                    // is present (the subArray will be empty if not):
                    if (salt.length == 0) {
                        throw new MissingMigrationError("ChallengePublicKey salt");
                    }

                    return new ChallengePublicKey(publicKey, salt);
                });
    }

    /**
     * Return true if ChallengeKeys have already been migrated.
     */
    public boolean hasMigratedChallengeKeys() {
        return getChallengePublicKey(ChallengeType.PASSWORD)
                .compose(ObservableFn.onTypedErrorResumeNext(
                        MissingMigrationError.class,
                        error -> Observable.just(false)
                ))
                .map(key -> true)
                .toBlocking()
                .first();
    }

    private boolean hasChallengePublicKey(ChallengeType type) {
        return secureStorageProvider.has(KEY_CHALLENGE_PUBLIC_KEY + type.toString());
    }

    /**
     * Saves a string containing an encrypted private muun key.
     *
     * @param encryptedPrivateMuunKey the encrypted key plus metadata.
     */
    public void storeEncryptedMuunPrivateKey(String encryptedPrivateMuunKey) {

        Timber.d("Stored encrypted muun key on secure storage.");

        secureStorageProvider.put(
                KEY_ENCRYPTED_PRIVATE_MUUN_KEY,
                encryptedPrivateMuunKey.getBytes()
        );
    }

    public Observable<String> getEncryptedMuunPrivateKey() {
        return secureStorageProvider.getAsync(KEY_ENCRYPTED_PRIVATE_MUUN_KEY)
                .map(String::new);
    }

    /**
     * Stores a public challenge key on secure storage.
     *
     * @param publicKey challenge public key.
     * @param type      the challenge type.
     */
    public void storePublicChallengeKey(ChallengePublicKey publicKey, ChallengeType type) {

        Timber.d("Storing challenge public key %s on secure storage.", type.toString());

        if (publicKey.getSalt() == null) {
            throw new MissingMigrationError("ChallengePublicKey salt");
        }

        secureStorageProvider.put(
                KEY_CHALLENGE_PUBLIC_KEY + type.toString(),
                ByteUtils.concatenate(publicKey.toBytes(), publicKey.getSalt())
        );

        // Update the encrypted Apollo private key when some challenge key changes.
        if (secureStorageProvider.has(KEY_BASE_58_PRIVATE_KEY)) {
            getBasePrivateKey()
                    .flatMap(this::storeEncryptedBasePrivateKey)
                    .toBlocking()
                    .first();
        }
    }

    /**
     * Add the salt to an existing challenge key for old sessions.
     *
     * <p>This is compat method and should not be used for anything else.</p>
     */
    public void seasonPublicChallengeKey(final byte[] salt, final ChallengeType type) {
        Logger.debug("Adding salt to %s", type.toString());

        final byte[] publicKey = secureStorageProvider
                .get(KEY_CHALLENGE_PUBLIC_KEY + type.toString());

        if (publicKey.length != ChallengePublicKey.PUBLIC_KEY_LENGTH) {
            Logger.error("Tried to migrate salt for key %s more than once", type.toString());
            return;
        }

        storePublicChallengeKey(new ChallengePublicKey(publicKey, salt), type);
    }

    @Override
    protected String getFileName() {
        return "keys";
    }

    /**
     * Returns the max used external address index or null if none has been used.
     */
    @Nullable
    public Integer getMaxUsedExternalAddressIndex() {
        if (maxUsedExternalAddressIndexPreference.isSet()) {
            return maxUsedExternalAddressIndexPreference.get();
        } else {
            return null;
        }
    }

    public Integer getMaxWatchingExternalAddressIndex() {
        return maxWatchingExternalAddressIndexPreference.get();
    }

    public void setMaxUsedExternalAddressIndex(Integer maxUsedExternalAddressIndex) {
        this.maxUsedExternalAddressIndexPreference.set(maxUsedExternalAddressIndex);
    }

    public void setMaxWatchingExternalAddressIndex(Integer maxWatchingExternalAddressIndex) {
        this.maxWatchingExternalAddressIndexPreference.set(maxWatchingExternalAddressIndex);
    }
}
