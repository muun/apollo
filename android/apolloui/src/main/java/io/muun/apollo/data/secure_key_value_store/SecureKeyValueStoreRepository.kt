package io.muun.apollo.data.secure_key_value_store

import io.muun.apollo.osmodules.securekv.Secret
import io.muun.apollo.osmodules.securekv.SecureKeyValueStore
import javax.inject.Inject

class SecureKeyValueStoreRepository @Inject internal constructor(
    private val store: SecureKeyValueStore,
) {

    /**
     * Store [value] under [key]. The array is not wiped here: only the caller knows whether it
     * holds secret material, so callers passing plaintext secrets zero it once put returns (see
     * AuthRepository.storeServerJwt).
     */
    fun put(key: String, value: ByteArray) {
        store.put(key, value)
    }

    fun get(key: String): Secret {
        return store.get(key)
    }

    @Deprecated("Goes away once the wallet's storage lives in libwallet, which never checks first")
    fun has(key: String): Boolean {
        return store.has(key)
    }

    fun delete(key: String) {
        store.delete(key)
    }

    fun wipe() {
        store.wipe()
    }
}
