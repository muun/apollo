package io.muun.apollo.domain.action.fcm

import androidx.annotation.VisibleForTesting
import io.muun.apollo.data.async.gcm.FirebaseManager
import io.muun.apollo.domain.action.base.BaseAsyncAction0
import rx.Observable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@VisibleForTesting // open (non-final) class so mockito can mock/spy
open class ForceFetchFcmAction @Inject constructor(
    private val firebaseManager: FirebaseManager,
) : BaseAsyncAction0<String>() {

    override fun action(): Observable<String> {
        return Observable.defer {
            firebaseManager.fetchFcmToken()
        }
    }
}