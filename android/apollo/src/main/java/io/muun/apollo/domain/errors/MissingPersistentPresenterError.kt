package io.muun.apollo.domain.errors

class MissingPersistentPresenterError(
    presenterClass: Class<Any>,
    presentersInCache: List<String>,
) : MuunError() {

    init {
        metadata["presenter"] = presenterClass.simpleName
        metadata["presenterCacheSize"] = presentersInCache.size
        metadata["presenterCacheValues"] = presentersInCache.joinToString(",")
    }

}