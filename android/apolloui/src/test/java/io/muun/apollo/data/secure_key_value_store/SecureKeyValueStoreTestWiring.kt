package io.muun.apollo.data.secure_key_value_store

import io.muun.apollo.data.os.secure_storage.FakeKeyStore
import io.muun.apollo.data.os.secure_storage.FakePreferences
import io.muun.apollo.data.os.secure_storage.SecureStorageProvider
import io.muun.apollo.osmodules.securekv.SecureKeyValueStore

/**
 * Production wiring of the securekv repository over a (fake-backed) legacy provider, so tests
 * that seed state through the provider and read it through the repository share one storage.
 */
internal object SecureKeyValueStoreTestWiring {

    fun repositoryOver(provider: SecureStorageProvider): SecureKeyValueStoreRepository {
        return SecureKeyValueStoreRepository(SecureKeyValueStore(provider))
    }

    /**
     * Same wiring for tests that only reach the storage through the repository.
     */
    fun repository(): SecureKeyValueStoreRepository {
        return repositoryOver(SecureStorageProvider(FakeKeyStore(), FakePreferences()))
    }
}
