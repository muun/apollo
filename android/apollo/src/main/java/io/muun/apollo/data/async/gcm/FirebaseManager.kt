package io.muun.apollo.data.async.gcm

import android.os.AsyncTask
import com.google.android.gms.tasks.Task
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.iid.InstanceIdResult
import io.muun.apollo.domain.action.fcm.UpdateFcmTokenAction
import io.muun.apollo.domain.errors.FcmTokenCanceledError
import io.muun.apollo.domain.errors.FcmTokenError
import io.muun.apollo.domain.errors.MuunError
import rx.Observable
import rx.subjects.BehaviorSubject
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseManager @Inject constructor(private val updateFcmTokenAction: UpdateFcmTokenAction) {

    private val subject = BehaviorSubject.create<String>()

    /**
     * Manually fetch FCM token. Useful for force-fetching when hasn't arrive via
     * {@link GcmMessageListenerService#onNewToken}.
     */
    fun fetchFcmToken(): Observable<String> {

        FirebaseInstanceId.getInstance().instanceId
            .addOnCompleteListener { task ->

                if (!task.isSuccessful) {
                    val error = getError(task)
                    Timber.e(error)
                    subject.onError(error)
                    return@addOnCompleteListener
                }

                val result = checkNotNull(task.result)

                Timber.i("GetInstanceId SUCCESS: " + result.token)

                updateFcmTokenAction.run(result.token)
                subject.onNext(result.token)
            }

        return subject
    }

    private fun getError(task: Task<InstanceIdResult>): MuunError {
        return if (task.isCanceled) {
            FcmTokenCanceledError()

        } else {
            FcmTokenError(task.exception ?: RuntimeException("Null underlying error!"))
        }
    }

    /**
     * Delete FCM Token, forcing a new token generation, which should arrive via/trigger
     * {@link GcmMessageListenerService#onNewToken}.
     */
    private fun resetFcmToken() {

        // This needs to happen outside the main thread (see code for deleteInstanceId())
        AsyncTask.execute {
            try {
                FirebaseInstanceId.getInstance().deleteInstanceId()
                Timber.i("InstanceID DELETED")

            } catch (e: IOException) {
                Timber.e(FcmTokenError(e))
            }
        }
    }
}