package io.muun.apollo.domain.errors

import io.muun.apollo.data.os.secure_storage.SecureStorageProvider
import java.io.Serializable

open class MuunError: RuntimeException {

    constructor()
    constructor(message: String): super(message)
    constructor(cause: Throwable): super(cause)
    constructor(message: String, cause: Throwable): super(message, cause)

    val metadata = HashMap<String, Serializable>()

    // TODO: generate message using metadata, at least in debug (use Crashlytics keys in prod)

    /**
     * Extract metadata for error report crafting. We prefix metadata keys with the error name
     * to clear differentiate with metadata coming from other errors (e.g. the cause of a MuunError
     * could be another MuunError with its own metadata).
     */
    fun extractMetadata(): MutableMap<String, Serializable> {
        val mapKeys = metadata.mapKeys { entry -> "${javaClass.canonicalName}.${entry.key}" }
        return mapKeys as MutableMap<String, Serializable>
    }
}