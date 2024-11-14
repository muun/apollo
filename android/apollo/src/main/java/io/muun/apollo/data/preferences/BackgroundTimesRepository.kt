package io.muun.apollo.data.preferences

import android.content.Context
import io.muun.apollo.data.preferences.adapter.JsonListPreferenceAdapter
import io.muun.apollo.data.preferences.rx.Preference
import io.muun.apollo.domain.model.BackgroundEvent
import javax.inject.Inject

class BackgroundTimesRepository @Inject constructor(
    context: Context,
    repositoryRegistry: RepositoryRegistry,
) : BaseRepository(context, repositoryRegistry) {

    companion object {
        private const val BACKGROUND_TIMES_KEY = "background_times_key"
        private const val LAST_BACKGROUND_BEGIN_TIME_KEY = "last_background_begin_time_key"
    }

    private class StoredBackgroundEvent {
        var beginTimeInMillis: Long = 0
        var durationInMillis: Long = 0

        /**
         * Constructor from the model.
         */
        constructor(bkgEvent: BackgroundEvent) {
            beginTimeInMillis = bkgEvent.beginTimeInMillis
            durationInMillis = bkgEvent.durationInMillis

        }

        /**
         * JSON constructor.
         */
        @Suppress("unused")
        constructor()

        fun toModel(): BackgroundEvent {
            return BackgroundEvent(
                beginTimeInMillis,
                durationInMillis
            )
        }
    }

    override val fileName get() = "background_times"

    private val lastBackgroundBeginTimePreference: Preference<Long?>
        get() = rxSharedPreferences.getLong(LAST_BACKGROUND_BEGIN_TIME_KEY, null)

    private val backgroundTimesPreferences: Preference<List<StoredBackgroundEvent>>
        get() = rxSharedPreferences.getObject(
            BACKGROUND_TIMES_KEY,
            emptyList(),
            JsonListPreferenceAdapter(StoredBackgroundEvent::class.java)
        )

    fun recordEnterBackground() {
        lastBackgroundBeginTimePreference.set(System.currentTimeMillis())
    }

    fun getLastBackgroundBeginTime(): Long? {
        return lastBackgroundBeginTimePreference.get()
    }

    fun recordBackgroundEvent(bkgBeginTime: Long, duration: Long) {
        val storedBkgTimes = getBackgroundTimes()
        val bkgTimes = storedBkgTimes.toMutableList()

        bkgTimes.add(BackgroundEvent(bkgBeginTime, duration))

        storeBkgTimes(bkgTimes)
        lastBackgroundBeginTimePreference.set(null)
    }

    fun getBackgroundTimes(): List<BackgroundEvent> {
        return backgroundTimesPreferences.get()!!.map { it.toModel() }
    }

    fun pruneIfGreaterThan(maxBkgTimesArraySize: Int) {
        val storedBkgTimes = getBackgroundTimes()
        val bkgTimes = storedBkgTimes.takeLast(maxBkgTimesArraySize)

        storeBkgTimes(bkgTimes)
    }

    private fun storeBkgTimes(bkgTimes: List<BackgroundEvent>) {
        val storedBkgTimes = bkgTimes.map { it.toJson() }
        backgroundTimesPreferences.set(storedBkgTimes)
    }

    private fun BackgroundEvent.toJson(): StoredBackgroundEvent =
        StoredBackgroundEvent(this)
}