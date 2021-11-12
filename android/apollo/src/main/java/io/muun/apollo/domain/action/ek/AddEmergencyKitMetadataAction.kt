package io.muun.apollo.domain.action.ek

import io.muun.apollo.data.fs.FileCache
import io.muun.apollo.data.fs.FileCache.Entry
import io.muun.apollo.domain.action.base.BaseAsyncAction1
import libwallet.Libwallet
import rx.Observable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AddEmergencyKitMetadataAction @Inject constructor(
    private val fileCache: FileCache
): BaseAsyncAction1<String, Void>() {

    override fun action(metadata: String): Observable<Void> =
        Observable.defer { createFileWithMetadata(metadata) }

    private fun createFileWithMetadata(metadata: String): Observable<Void> {
        Libwallet.addEmergencyKitMetadata(
            metadata,
            fileCache.get(Entry.EMERGENCY_KIT_NO_META).path, // source
            fileCache.get(Entry.EMERGENCY_KIT).path // destination
        )
        
        return Observable.just(null)
    }

}
