package io.muun.apollo.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import io.muun.apollo.data.preferences.rx.RxSharedPreferences

abstract class BaseRepository(context: Context, repositoryRegistry: RepositoryRegistry) {

    protected val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(
            fileName,
            Context.MODE_PRIVATE
        )
    }

    protected val rxSharedPreferences: RxSharedPreferences by lazy {
        RxSharedPreferences.create(sharedPreferences)
    }

    /**
     * Creates a base preferences repository.
     */
    init {
        @Suppress("LeakingThis")
        repositoryRegistry.load(this)
    }

    protected abstract val fileName: String

    /**
     * Clears the repository.
     */
    fun clear() {
        sharedPreferences.edit()
            .clear()
            .commit()
    }

    @VisibleForTesting
    fun getSharedPreferencesForTesting() =
        sharedPreferences

    @VisibleForTesting
    fun getRxSharedPreferencesForTesting() =
        rxSharedPreferences
}