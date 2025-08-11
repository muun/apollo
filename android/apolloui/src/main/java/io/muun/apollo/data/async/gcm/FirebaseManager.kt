package io.muun.apollo.data.async.gcm

import android.os.AsyncTask
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import io.muun.apollo.domain.action.fcm.UpdateFcmTokenAction
import io.muun.apollo.domain.errors.MuunError
import io.muun.apollo.domain.errors.fcm.FcmTokenCanceledError
import io.muun.apollo.domain.errors.fcm.FcmTokenError
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

        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->

                if (!task.isSuccessful) {
                    val error = getError(task)
                    Timber.e(error)
                    subject.onError(error)
                    return@addOnCompleteListener
                }

                val token = checkNotNull(task.result)

                Timber.i("GetInstanceId SUCCESS: $token")

                updateFcmTokenAction.run(token)
                subject.onNext(token)
            }

        return subject
    }

    private fun getError(task: Task<String>): MuunError {
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
                FirebaseMessaging.getInstance().deleteToken()
                Timber.i("InstanceID DELETED")

            } catch (e: IOException) {
                Timber.e(FcmTokenError(e))
            }
        }
    }
}