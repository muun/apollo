package io.muun.apollo.data.nfc

import dagger.Lazy
import io.muun.apollo.domain.libwallet.LibwalletClient
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Write-through in-memory cache for the empirical NFC Extended APDU signals persisted in
 * libwallet's key-value storage. Reads on the hot path (BackgroundExecutionMetrics
 * serialisation, once per outgoing HTTP request) are pure volatile field reads with no
 * gRPC involved. Writes update memory first and then persist to KV.
 *
 * The cache is warmed up once at application start via [initFromStorage], so a value
 * persisted in a previous session is visible from the very first BEM serialisation.
 * On tap, [update] refreshes the cache directly so subsequent BEMs see the fresh value
 * immediately.
 *
 * `LibwalletClient` is held as `dagger.Lazy` to break the DI cycle at graph-resolution
 * time — the libwallet client is only resolved from [initFromStorage] and [update],
 * both of which run after libwallet is up.
 */
@Singleton
class NfcEmpiricalCache @Inject constructor(
    private val libwalletClient: Lazy<LibwalletClient>,
) {

    companion object {
        // KV keys registered in libwallet/storage/kv_migrations.go — keep the strings in sync.
        private const val EXTENDED_APDU_SUPPORTED = "nfc_extended_apdu_supported"
        private const val MAX_TRANSCEIVE_LENGTH = "nfc_max_transceive_length"
    }

    @Volatile
    var extendedApduSupported: Boolean? = null
        private set

    @Volatile
    var maxTransceiveLength: Int? = null
        private set

    /**
     * Warm the cache with values persisted in a previous session. Must be called after
     * libwallet's gRPC server has started, before the first BEM serialisation can fire.
     */
    fun initFromStorage() {
        try {
            val client = libwalletClient.get()
            extendedApduSupported = client.getBoolean(EXTENDED_APDU_SUPPORTED)
            maxTransceiveLength = client.getInt(MAX_TRANSCEIVE_LENGTH)
        } catch (t: Throwable) {
            Timber.i("Failed to read empirical NFC signals from storage: ${t.message}")
        }
    }

    /**
     * Called on a successful NFC tap: refresh the in-memory cache and persist to KV.
     */
    fun update(supported: Boolean, maxTransceiveLength: Int) {
        this.extendedApduSupported = supported
        this.maxTransceiveLength = maxTransceiveLength
        try {
            val client = libwalletClient.get()
            client.saveBoolean(EXTENDED_APDU_SUPPORTED, supported)
            client.saveInt(MAX_TRANSCEIVE_LENGTH, maxTransceiveLength)
        } catch (t: Throwable) {
            Timber.i("Failed to persist NFC Extended APDU capability: ${t.message}")
        }
    }
}
