package io.muun.apollo.data.preferences

import android.content.Context
import io.muun.apollo.data.preferences.rx.Preference
import javax.inject.Inject

// Open for mockito to mock/spy
open class NotificationRepository @Inject constructor(
    context: Context,
    repositoryRegistry: RepositoryRegistry,
) : BaseRepository(context, repositoryRegistry) {

    companion object {
        private const val KEY_LAST_PROCESSED_ID = "last_processed_id"
        private const val KEY_PROCESSING_FAILURES = "processing_failures"
    }

    private val lastProcessedIdPreference: Preference<Long>
        get() = rxSharedPreferences.getLong(KEY_LAST_PROCESSED_ID, 0L)
    private val processingFailuresPreference: Preference<Long>
        get() = rxSharedPreferences.getLong(KEY_PROCESSING_FAILURES, 0L)

    override val fileName get() = "notifications"

    var lastProcessedId: Long
        get() = lastProcessedIdPreference.get()!!
        set(lastConfirmedId) {
            if (lastProcessedId < lastConfirmedId) {
                processingFailuresPreference.set(0L)
            }
            lastProcessedIdPreference.set(lastConfirmedId)
        }

    fun increaseProcessingFailures() {
        processingFailuresPreference.set(processingFailures + 1)
    }

    val processingFailures: Long
        get() = processingFailuresPreference.get()!!
}