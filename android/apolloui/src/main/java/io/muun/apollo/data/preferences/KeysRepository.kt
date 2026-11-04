package io.muun.apollo.data.preferences

import android.content.Context
import io.muun.apollo.data.os.secure_storage.SecureStorageProvider
import io.muun.apollo.data.preferences.adapter.PublicKeyPreferenceAdapter
import io.muun.apollo.data.preferences.rx.Preference
import io.muun.apollo.data.secure_key_value_store.SecureKeyValueStoreRepository
import io.muun.apollo.domain.errors.BugDetected
import io.muun.apollo.domain.errors.MissingMigrationError
import io.muun.apollo.domain.errors.SecureKeyValueStoreError
import io.muun.apollo.domain.utils.toVoid
import io.muun.common.crypto.ChallengePublicKey
import io.muun.common.crypto.ChallengeType
import io.muun.common.crypto.hd.PrivateKey
import io.muun.common.crypto.hd.PublicKey
import io.muun.common.crypto.hd.PublicKeyPair
import io.muun.common.crypto.hd.PublicKeyTriple
import io.muun.common.crypto.hd.Schema
import io.muun.common.rx.ObservableFn
import io.muun.common.utils.Preconditions
import org.bitcoinj.core.NetworkParameters
import rx.Observable
import timber.log.Timber
import javax.inject.Inject

// Open for mockito to mock/spy
open class KeysRepository @Inject constructor(
    context: Context,
    repositoryRegistry: RepositoryRegistry,
    // Dependencies:
    private val networkParameters: NetworkParameters,
    private val secureKeyValueStoreRepository: SecureKeyValueStoreRepository,
    private val userRepository: UserRepository,
    private val secureStorageProvider: SecureStorageProvider,
) : BaseRepository(context, repositoryRegistry) {

    companion object {
        // Storage-level keys:
        private const val KEY_BASE_PRIVATE_KEY_PATH = "xpriv_path"
        // libwallet reads the user key by this name (app_provided_data/adapted/key_provider.go).
        // Renaming it breaks that read at runtime.
        private const val KEY_BASE_58_PRIVATE_KEY = "key_base_58_private_key"
        private const val KEY_BASE_PUBLIC_KEY = "base_public_key"
        private const val KEY_CHALLENGE_PUBLIC_KEY = "key_challenge_public_key_"
        private const val KEY_ENCRYPTED_PRIVATE_USER_KEY = "key_encrypted_private_apollo_key"
        private const val KEY_ENCRYPTED_PRIVATE_MUUN_KEY = "key_encrypted_private_muun_key"
        private const val KEY_MUUN_KEY_FINGERPRINT = "fingerprint_muun_key"
        private const val KEY_USER_KEY_FINGERPRINT = "fingerprint_user_key"
        private const val KEY_BASE_MUUN_PUBLIC_KEY = "base_muun_public_key"
        private const val KEY_BASE_SWAP_SERVER_PUBLIC_KEY = "base_swap_server_public_key"
        private const val KEY_MAX_USED_EXTERNAL_ADDRESS_INDEX =
            "key_max_used_external_address_index"
        private const val KEY_MAX_WATCHING_EXTERNAL_ADDRESS_INDEX =
            "key_max_watching_external_address_index"
    }

    // Reactive preferences:
    private val basePrivateKeyPathPreference: Preference<String>
        get() = rxSharedPreferences.getString(KEY_BASE_PRIVATE_KEY_PATH)

    private val basePublicKeyPreference: Preference<PublicKey>
        get() = rxSharedPreferences.getObject(
            KEY_BASE_PUBLIC_KEY,
            PublicKeyPreferenceAdapter.INSTANCE
        )

    private val baseMuunPublicKeyPreference: Preference<PublicKey>
        get() = rxSharedPreferences.getObject(
            KEY_BASE_MUUN_PUBLIC_KEY,
            PublicKeyPreferenceAdapter.INSTANCE
        )

    private val baseSwapServerPublicKeyPreference: Preference<PublicKey>
        get() = rxSharedPreferences.getObject(
            KEY_BASE_SWAP_SERVER_PUBLIC_KEY,
            PublicKeyPreferenceAdapter.INSTANCE
        )
    private val muunKeyFingerprintPreference: Preference<String>
        get() = rxSharedPreferences.getString(KEY_MUUN_KEY_FINGERPRINT)

    private val userKeyFingerprintPreference: Preference<String>
        get() = rxSharedPreferences.getString(KEY_USER_KEY_FINGERPRINT)

    private val maxUsedExternalAddressIndexPreference: Preference<Int>
        get() = rxSharedPreferences.getInteger(KEY_MAX_USED_EXTERNAL_ADDRESS_INDEX)

    private val maxWatchingExternalAddressIndexPreference: Preference<Int>
        get() = rxSharedPreferences.getInteger(KEY_MAX_WATCHING_EXTERNAL_ADDRESS_INDEX)

    override val fileName get() = "keys"

    /**
     * Returns a new non-stored root PrivateKey.
     */
    fun createRootPrivateKey(): PrivateKey {
        return PrivateKey.getNewRootPrivateKey(networkParameters)
    }

    /**
     * Returns the current base private key.
     */
    val basePrivateKey: Observable<PrivateKey>
        get() = Observable.fromCallable {
            val path = basePrivateKeyPathPreference.get()
            // TODO: two copies escape the wipe here: the String, which the JVM can't clear, and
            // the PrivateKey, which keeps the material in an immutable BigInteger. A
            // withBasePrivateKey(consume) would only shorten how long they live, since it still
            // builds a PrivateKey; the way out is libwallet reading the key itself. Don't add
            // callers of this getter, and don't read it as licence to build a String from a
            // Secret elsewhere.
            var basePrivateKey: PrivateKey? = null

            withStoredSecret(KEY_BASE_58_PRIVATE_KEY) { base58Bytes ->
                basePrivateKey = PrivateKey.deserializeFromBase58(path, String(base58Bytes))
            }

            requireNotNull(basePrivateKey)
        }

    val basePrivateKeyPath: String
        get() = requireNotNull(basePrivateKeyPathPreference.get()) {
            "Base private key path must NOT be null!"
        }

    @Suppress("DEPRECATION")
    val hasBasePrivateKey: Boolean
        get() = secureKeyValueStoreRepository.has(KEY_BASE_58_PRIVATE_KEY)

    /**
     * Whether the base private key is stored but can no longer be decrypted on this device,
     * because its keystore key is gone. Never throws: answers false when the keystore can't be
     * queried, so act only on true.
     *
     * Goes straight to the legacy provider: it only probes the keystore and never reads the
     * value, which SecureKeyValueStoreRepository has no door for. Goes away once the wallet's
     * storage lives in libwallet.
     */
    val isBasePrivateKeyKeystoreKeyMissing: Boolean
        get() = secureStorageProvider.isKeystoreKeyMissing(KEY_BASE_58_PRIVATE_KEY)

    /**
     * Returns the base public key.
     */
    val basePublicKey: PublicKey
        get() {
            val publicKey = basePublicKeyPreference.get()
            Preconditions.checkState(publicKey != null, "PublicKey must NOT be null!")
            return publicKey!!
        }

    /**
     * Returns the base Muun cosigning public key.
     */
    val baseMuunPublicKey: PublicKey
        get() {
            val publicKey = baseMuunPublicKeyPreference.get()
            Preconditions.checkState(
                publicKey != null,
                "Muun cosigning PublicKey must NOT be null!"
            )
            return publicKey!!
        }

    /**
     * Returns the swap server public key for this user.
     */
    private val baseSwapServerPublicKey: PublicKey?
        get() = baseSwapServerPublicKeyPreference.get()

    /**
     * Returns the base user-Muun public key pair.
     */
    @get:Deprecated("use getBasePublicKeyTriple", ReplaceWith("basePublicKeyTriple"))
    val basePublicKeyPair: PublicKeyPair
        get() = PublicKeyPair(
            basePublicKey,
            baseMuunPublicKey
        )

    /**
     * Returns the base user-Muun-swapServer public key triple.
     */
    val basePublicKeyTriple: PublicKeyTriple
        get() = PublicKeyTriple(
            basePublicKey,
            baseMuunPublicKey,
            baseSwapServerPublicKey
        )

    /**
     * Save the base Muun cosigning public key.
     */
    fun storeBaseMuunPublicKey(publicKey: PublicKey) {
        baseMuunPublicKeyPreference.set(publicKey)
    }

    /**
     * Save the base swap server key for this user.
     */
    fun storeSwapServerPublicKey(publicKey: PublicKey) {
        baseSwapServerPublicKeyPreference.set(publicKey)
    }

    /**
     * Saves the base private key from which all are derived.
     */
    fun storeBasePrivateKey(privateKey: PrivateKey): Observable<Void> {
        return Observable.fromCallable {
            // The private key can be rooted at different derivation paths depending on
            // when it was generated. Older apps rooted them at m. The now expected path is
            // Schema#getBasePath, so we derive to it to ensure consistency for older keys.
            val basePrivateKey = privateKey.deriveFromAbsolutePath(Schema.getBasePath())
            // TODO: store the key without going through a PrivateKey or a String. Only the
            // array handed to put is zeroed: the String serializeBase58 builds, which the JVM
            // can't clear, and both PrivateKeys, which keep the material in an immutable
            // BigInteger, outlive the wipe.
            val base58 = basePrivateKey.serializeBase58().toByteArray()

            try {
                secureKeyValueStoreRepository.put(KEY_BASE_58_PRIVATE_KEY, base58)
            } finally {
                base58.fill(0)
            }

            basePrivateKeyPathPreference.set(basePrivateKey.absoluteDerivationPath)
            basePublicKeyPreference.set(basePrivateKey.publicKey)
            maxUsedExternalAddressIndexPreference.set(-1) // set a default
        }.toVoid()
    }

    val encryptedBasePrivateKey: Observable<String>
        get() = readStoredString(KEY_ENCRYPTED_PRIVATE_USER_KEY)

    @Suppress("DEPRECATION")
    val hasEncryptedBasePrivateKey: Boolean
        get() = secureKeyValueStoreRepository.has(KEY_ENCRYPTED_PRIVATE_USER_KEY)

    /**
     * Saves a string containing an encrypted private base key.
     *
     * @param encryptedBasePrivateKey the encrypted key plus metadata.
     */
    fun storeEncryptedBasePrivateKey(encryptedBasePrivateKey: String) {
        Timber.d("Stored encrypted base key on secure storage.")
        secureKeyValueStoreRepository.put(
            KEY_ENCRYPTED_PRIVATE_USER_KEY,
            encryptedBasePrivateKey.toByteArray()
        )
    }

    fun wipeEncryptedBasePrivateKey() {
        secureKeyValueStoreRepository.delete(KEY_ENCRYPTED_PRIVATE_USER_KEY)
    }

    /**
     * Obtain from secure storage the ChallengePublicKey for a ChallengeType.
     */
    fun getChallengePublicKey(type: ChallengeType): Observable<ChallengePublicKey> {
        return Observable.fromCallable {
            // TODO: migrate to withChallengePublicKey(consume) so the key never leaves the
            // callback. The ChallengePublicKey built here outlives the wipe. Don't add callers.
            var challengePublicKey: ChallengePublicKey? = null

            withStoredSecret(KEY_CHALLENGE_PUBLIC_KEY + type.toString()) { serialization ->

                // Latest ChallengePublicKey.deserialize() assumes pub key serialization has
                // salt (and pub key version). If the length of the pub key serialization
                // doesn't include the salt, it means the salt migration hasn't run. We need
                // that to run to properly migrated ChallengePublicKey to have salt and version.
                if (serialization.size == ChallengePublicKey.PUBLIC_KEY_LENGTH) {
                    throw MissingMigrationError("ChallengePublicKey salt")
                }
                val deserialized = ChallengePublicKey.deserialize(serialization)

                // NOTE:
                // Old Apollo versions did not store the challenge key salt. Modern Apollo
                // versions concatenate the salt to the public key bytes (see the store method
                // below). We use the length of the stored key to determine whether the salt
                // is present (the subArray will be empty if not):
                if (requireNotNull(deserialized.salt).isEmpty()) {
                    throw MissingMigrationError("ChallengePublicKey salt")
                }
                challengePublicKey = deserialized
            }

            requireNotNull(challengePublicKey)
        }
    }

    /**
     * Return true if ChallengeKeys have already been migrated.
     */
    fun hasMigratedChallengeKeys(): Boolean {
        return getChallengePublicKey(ChallengeType.PASSWORD)
            .map { true }
            .compose(ObservableFn.onTypedErrorResumeNext(
                SecureKeyValueStoreError.NotFound::class.java
            ) { Observable.just(true) } // nothing to migrate if the user is UU
            )
            .compose(ObservableFn.onTypedErrorResumeNext(
                MissingMigrationError::class.java
            ) { Observable.just(false) })
            .toBlocking()
            .first()
    }

    /**
     * Return true if the public key for a ChallengeType is present in storage.
     */
    @Suppress("DEPRECATION")
    fun hasChallengePublicKey(type: ChallengeType): Boolean {
        return secureKeyValueStoreRepository.has(KEY_CHALLENGE_PUBLIC_KEY + type.toString())
    }

    /**
     * Saves a string containing an encrypted private muun key.
     *
     * @param encryptedPrivateMuunKey the encrypted key plus metadata.
     */
    fun storeEncryptedMuunPrivateKey(encryptedPrivateMuunKey: String) {
        Timber.d("Stored encrypted muun key on secure storage.")
        secureKeyValueStoreRepository.put(
            KEY_ENCRYPTED_PRIVATE_MUUN_KEY,
            encryptedPrivateMuunKey.toByteArray()
        )
    }

    val encryptedMuunPrivateKey: Observable<String>
        get() = readStoredString(KEY_ENCRYPTED_PRIVATE_MUUN_KEY)

    @Suppress("DEPRECATION")
    val hasEncryptedMuunPrivateKey: Boolean
        get() = secureKeyValueStoreRepository.has(KEY_ENCRYPTED_PRIVATE_MUUN_KEY)

    /**
     * Save a fingerprint corresponding to Muun's private key.
     */
    fun storeMuunKeyFingerprint(muunKeyFingerprint: String) {
        muunKeyFingerprintPreference.set(muunKeyFingerprint)
    }

    val muunKeyFingerprint: Observable<String>
        get() = muunKeyFingerprintPreference.asObservable()

    /**
     * Save a fingerprint corresponding to the user private key.
     */
    fun storeUserKeyFingerprint(userKeyFingerprint: String) {
        userKeyFingerprintPreference.set(userKeyFingerprint)
    }

    val userKeyFingerprint: Observable<String>
        get() = userKeyFingerprintPreference.asObservable()

    /**
     * Stores a public challenge key on secure storage.
     *
     * @param publicKey challenge public key.
     * @param type      the challenge type.
     */
    fun storePublicChallengeKey(
        publicKey: ChallengePublicKey,
        type: ChallengeType,
    ) {
        Timber.d("Storing challenge public key %s on secure storage.", type.toString())
        if (publicKey.salt == null) {
            throw MissingMigrationError("ChallengePublicKey salt")
        }
        secureKeyValueStoreRepository.put(
            KEY_CHALLENGE_PUBLIC_KEY + type,
            publicKey.serialize()
        )

        // The encrypted base private key is encrypted with the RECOVERY_CODE challenge public key.
        // If that key rotates, the cached encrypted base private key becomes stale (it was encrypted
        // with the old key and can no longer be decrypted correctly). We wipe it here so that
        // GetOrCreateEncryptedBasePrivateKeyAction re-encrypts it with the new key on the next
        // emergency kit export.
        //
        // A RECOVERY_CODE challenge key can rotate when:
        //   - The user sets up a recovery code for the first time (not a problem).
        //   - Legacy migrations run (seasonPublicChallengeKey,
        //     addChallengePublicKeyVersionMigration)
        //
        // We only do this if the base private key exists, since there is nothing to wipe for
        // users who haven't completed wallet setup yet.
        if (type == ChallengeType.RECOVERY_CODE && hasBasePrivateKey) {
            wipeEncryptedBasePrivateKey()
        }
    }

    /**
     * Add the salt to an existing challenge key for old sessions.
     *
     *
     * This is compat method and should not be used for anything else.
     */
    fun seasonPublicChallengeKey(salt: ByteArray, type: ChallengeType) {
        Timber.d("Adding salt to %s", type.toString())
        withStoredSecret(KEY_CHALLENGE_PUBLIC_KEY + type.toString()) { publicKey ->
            if (publicKey.size != ChallengePublicKey.PUBLIC_KEY_LENGTH) {
                Timber.e(BugDetected("Tried to double-migrate salt for key $type"))
            } else {
                storePublicChallengeKey(ChallengePublicKey.buildLegacy(publicKey, salt), type)
            }
        }
    }

    /**
     * Add version to an existing challenge key. Assumes that it has salt (e.g was created with one
     * or was correctly migrated, see method above).
     *
     *
     * This is compat method and should not be used for anything else.
     */
    fun addChallengePublicKeyVersionMigration(type: ChallengeType) {
        withStoredSecret(KEY_CHALLENGE_PUBLIC_KEY + type.toString()) { serialization ->
            // If serialization doesn't have salt, it means the salt migration has not run yet.
            // Skip this for now and let salt migration take care of it, when it runs.
            if (serialization.size == ChallengePublicKey.PUBLIC_KEY_LENGTH + 8) {
                storePublicChallengeKey(ChallengePublicKey.deserializeLegacy(serialization), type)
            }
        }
    }

    /**
     * Returns the max used external address index or null if none has been used.
     */
    var maxUsedExternalAddressIndex: Int?
        get() = if (maxUsedExternalAddressIndexPreference.isSet) {
            maxUsedExternalAddressIndexPreference.get()
        } else {
            null
        }
        /**
         * Save a new max used external address index. This means the maximum index which we have
         * used to generate an address.
         */
        set(maxUsedExternalAddressIndex) {
            maxUsedExternalAddressIndexPreference.set(maxUsedExternalAddressIndex)
        }

    /**
     * Save a new max watching external address index. This means the maximum index which we will
     * using for scanning address for incoming transactions.
     */
    var maxWatchingExternalAddressIndex: Int
        get() = maxWatchingExternalAddressIndexPreference.get()!!
        set(maxWatchingExternalAddressIndex) {
            maxWatchingExternalAddressIndexPreference.set(maxWatchingExternalAddressIndex)
        }

    /**
     * Consume the value under [key].
     *
     * A key with nothing under it raises [SecureKeyValueStoreError.NotFound]. Let it travel as
     * NotFound: turning it into a corruption error would put the secure storage dialog in front
     * of the user, and reading the same missing record through the legacy API does not.
     */
    private inline fun withStoredSecret(key: String, crossinline consume: (ByteArray) -> Unit) {
        secureKeyValueStoreRepository.get(key).withSecret(consume)
    }

    /**
     * Read the value under [key] back as a String.
     *
     * TODO: this reads ciphertext, so it should never have been handed a Secret at all. Fix it
     * at SecureKeyValueStoreRepository.get, which returns one for every key, and not by
     * relaxing Secret's rule against building Strings. Keep this the only place that builds a
     * String out of stored ciphertext, so the sites left to fix stay countable.
     */
    private fun readStoredString(key: String): Observable<String> {
        return Observable.fromCallable {
            var value: String? = null

            withStoredSecret(key) { bytes ->
                value = String(bytes)
            }

            requireNotNull(value)
        }
    }
}