package io.muun.apollo.data.preferences

import android.content.Context
import io.muun.apollo.data.os.secure_storage.SecureStorageProvider
import io.muun.apollo.data.os.secure_storage.SecureStorageProvider.SecureStorageNoSuchElementError
import io.muun.apollo.data.preferences.adapter.PublicKeyPreferenceAdapter
import io.muun.apollo.data.preferences.rx.Preference
import io.muun.apollo.domain.errors.BugDetected
import io.muun.apollo.domain.errors.MissingMigrationError
import io.muun.apollo.domain.utils.toVoid
import io.muun.common.crypto.ChallengePublicKey
import io.muun.common.crypto.ChallengeType
import io.muun.common.crypto.hd.PrivateKey
import io.muun.common.crypto.hd.PublicKey
import io.muun.common.crypto.hd.PublicKeyPair
import io.muun.common.crypto.hd.PublicKeyTriple
import io.muun.common.crypto.hd.Schema
import io.muun.common.rx.ObservableFn
import io.muun.common.utils.Encodings
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
    private val secureStorageProvider: SecureStorageProvider,
    private val userRepository: UserRepository,
) : BaseRepository(context, repositoryRegistry) {

    companion object {
        private const val SECONDS_IN_A_DAY = 60 * 60 * 24

        // Storage-level keys:
        private const val KEY_BASE_PRIVATE_KEY_PATH = "xpriv_path"
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
        get() = rxSharedPreferences.getObject(KEY_BASE_PUBLIC_KEY, PublicKeyPreferenceAdapter.INSTANCE)
    private val baseMuunPublicKeyPreference: Preference<PublicKey>
        get() = rxSharedPreferences.getObject(KEY_BASE_MUUN_PUBLIC_KEY, PublicKeyPreferenceAdapter.INSTANCE)
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
        get() = secureStorageProvider.getAsync(KEY_BASE_58_PRIVATE_KEY)
            .map { base58Bytes: ByteArray? ->
                val path = basePrivateKeyPathPreference.get()
                val base58 = String(base58Bytes!!)
                PrivateKey.deserializeFromBase58(path, base58)
            }

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
        // The private key can be rooted at different derivation paths depending on
        // when it was generated. Older apps rooted them at m. The now expected path is
        // Schema#getBasePath, so we derive to it to ensure consistency for older keys.
        val basePrivateKey = privateKey.deriveFromAbsolutePath(Schema.getBasePath())
        val base58 = basePrivateKey.serializeBase58().toByteArray()
        return secureStorageProvider.putAsync(KEY_BASE_58_PRIVATE_KEY, base58)
            .flatMap { storeEncryptedBasePrivateKey(basePrivateKey) }
            .doOnNext {
                basePrivateKeyPathPreference.set(basePrivateKey.absoluteDerivationPath)
                basePublicKeyPreference.set(basePrivateKey.publicKey)
                maxUsedExternalAddressIndexPreference.set(-1) // set a default
            }
            .toVoid() // Needed because JAVA!!! (rm after secureStorageProvider is kotlinized)
    }

    private fun storeEncryptedBasePrivateKey(basePrivateKey: PrivateKey): Observable<Void> {
        return if (!hasChallengePublicKey(ChallengeType.RECOVERY_CODE)) {
            // Without challenge keys, we can't store the exportable encrypted private key:
            Observable.just(null)
        } else getChallengePublicKey(ChallengeType.RECOVERY_CODE)
            .map { challengePublicKey: ChallengePublicKey ->
                val birthday = walletBirthdaySinceGenesis
                challengePublicKey.encryptPrivateKey(basePrivateKey, birthday)
            }
            .flatMap { encryptedKey: String ->
                Preconditions.checkNotNull(encryptedKey)
                Timber.d("Storing encrypted Apollo private key in secure storage.")
                storeUserKeyFingerprint(Encodings.bytesToHex(basePrivateKey.fingerprint))
                secureStorageProvider.putAsync(
                    KEY_ENCRYPTED_PRIVATE_USER_KEY,
                    encryptedKey.toByteArray()
                )
            }
    }

    // a placeholder, only for the user key. Not ideal, not necessary.
    private val walletBirthdaySinceGenesis: Long
        get() {
            val createdAt = userRepository.fetchOneOptional()
                .flatMap { it.createdAt }
                .orElse(null)
                ?: return 0xFFFF // a placeholder, only for the user key. Not ideal, not necessary.
            val creationTimestamp = createdAt.toEpochSecond()
            val genesisTimestamp = networkParameters.genesisBlock.timeSeconds
            return (creationTimestamp - genesisTimestamp) / SECONDS_IN_A_DAY
        }

    val encryptedBasePrivateKey: Observable<String>
        get() = secureStorageProvider.getAsync(KEY_ENCRYPTED_PRIVATE_USER_KEY)
            .map { bytes: ByteArray -> String(bytes) }

    /**
     * Obtain from secure storage the ChallengePublicKey for a ChallengeType.
     */
    fun getChallengePublicKey(type: ChallengeType): Observable<ChallengePublicKey> {
        return secureStorageProvider
            .getAsync(KEY_CHALLENGE_PUBLIC_KEY + type.toString())
            .map { serialization: ByteArray ->

                // Latest ChallengePublicKey.deserialize() assumes pub key serialization has
                // salt (and pub key version). If the length of the pub key serialization
                // doesn't include the salt, it means the salt migration hasn't run. We need
                // that to run to properly migrated ChallengePublicKey to have salt and version.
                if (serialization.size == ChallengePublicKey.PUBLIC_KEY_LENGTH) {
                    throw MissingMigrationError("ChallengePublicKey salt")
                }
                val challengePublicKey = ChallengePublicKey.deserialize(serialization)

                // NOTE:
                // Old Apollo versions did not store the challenge key salt. Modern Apollo
                // versions concatenate the salt to the public key bytes (see the store method
                // below). We use the length of the stored key to determine whether the salt
                // is present (the subArray will be empty if not):
                if (requireNotNull(challengePublicKey.salt).isEmpty()) {
                    throw MissingMigrationError("ChallengePublicKey salt")
                }
                challengePublicKey
            }
    }

    /**
     * Return true if ChallengeKeys have already been migrated.
     */
    fun hasMigratedChallengeKeys(): Boolean {
        return getChallengePublicKey(ChallengeType.PASSWORD)
            .map { true }
            .compose(ObservableFn.onTypedErrorResumeNext(
                SecureStorageNoSuchElementError::class.java
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
    fun hasChallengePublicKey(type: ChallengeType): Boolean {
        return secureStorageProvider.has(KEY_CHALLENGE_PUBLIC_KEY + type.toString())
    }

    /**
     * Saves a string containing an encrypted private muun key.
     *
     * @param encryptedPrivateMuunKey the encrypted key plus metadata.
     */
    fun storeEncryptedMuunPrivateKey(encryptedPrivateMuunKey: String) {
        Timber.d("Stored encrypted muun key on secure storage.")
        secureStorageProvider.put(
            KEY_ENCRYPTED_PRIVATE_MUUN_KEY,
            encryptedPrivateMuunKey.toByteArray()
        )
    }

    val encryptedMuunPrivateKey: Observable<String>
        get() = secureStorageProvider.getAsync(KEY_ENCRYPTED_PRIVATE_MUUN_KEY)
            .map { bytes: ByteArray -> String(bytes) }

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
    fun storePublicChallengeKey(publicKey: ChallengePublicKey, type: ChallengeType) {
        Timber.d("Storing challenge public key %s on secure storage.", type.toString())
        if (publicKey.salt == null) {
            throw MissingMigrationError("ChallengePublicKey salt")
        }
        secureStorageProvider.put(
            KEY_CHALLENGE_PUBLIC_KEY + type,
            publicKey.serialize()
        )

        // Update the encrypted Apollo private key when some challenge key changes.
        if (secureStorageProvider.has(KEY_BASE_58_PRIVATE_KEY)) {
            basePrivateKey
                .flatMap { basePrivateKey: PrivateKey ->
                    storeEncryptedBasePrivateKey(basePrivateKey)
                }
                .toBlocking()
                .first()
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
        val publicKey = secureStorageProvider[KEY_CHALLENGE_PUBLIC_KEY + type.toString()]
        if (publicKey.size != ChallengePublicKey.PUBLIC_KEY_LENGTH) {
            Timber.e(BugDetected("Tried to double-migrate salt for key $type"))
            return
        }
        storePublicChallengeKey(ChallengePublicKey.buildLegacy(publicKey, salt), type)
    }

    /**
     * Add version to an existing challenge key. Assumes that it has salt (e.g was created with one
     * or was correctly migrated, see method above).
     *
     *
     * This is compat method and should not be used for anything else.
     */
    fun addChallengePublicKeyVersionMigration(type: ChallengeType) {
        val legacyPublicKeySerialization =
            secureStorageProvider[KEY_CHALLENGE_PUBLIC_KEY + type.toString()]

        // If serialization doesn't have salt, it means the salt migration has not run yet. Skip
        // this for now and let salt migration take care of it, when it runs.
        if (legacyPublicKeySerialization.size != ChallengePublicKey.PUBLIC_KEY_LENGTH + 8) {
            return
        }
        val challengePublicKey = ChallengePublicKey.deserializeLegacy(
            legacyPublicKeySerialization
        )
        storePublicChallengeKey(challengePublicKey, type)
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
}